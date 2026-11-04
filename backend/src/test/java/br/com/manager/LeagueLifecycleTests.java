package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.controller.RegistrationController;
import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.JoinLeagueRequest;
import br.com.manager.models.requests.RegistrationRequest;
import br.com.manager.models.requests.UpdateTeamRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.service.AuthService;
import br.com.manager.service.CatalogService;
import br.com.manager.service.LeagueLifecycleService;
import br.com.manager.service.ManagementService;
import br.com.manager.service.SaveService;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lifecycle-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import(LeagueLifecycleTests.FixedTime.class)
@Transactional
class LeagueLifecycleTests {

    static final Instant NOW = Instant.parse("2030-06-01T12:00:00Z");

    @TestConfiguration
    static class FixedTime {

        @Bean
        @Primary
        Clock retentionTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    SaveService saves;

    @Autowired
    LeagueLifecycleService lifecycle;

    @Autowired
    ManagementService management;

    @Autowired
    CatalogService catalog;

    @Autowired
    JdbcTemplate db;

    @Autowired
    RegistrationController registration;

    @Autowired
    AuthService auth;

    LeagueAccessResponse create(int capacity) {
        return saves.create(
            new CreateLeagueRequest("Liga de teste", capacity, "Criador", catalog.teams(null).getFirst().id())
        );
    }

    String token(LeagueAccessResponse access) {
        return "Bearer " + access.accessToken();
    }

    Instant activity(String id) {
        return db.queryForObject("SELECT last_activity_at FROM game_saves WHERE id=?", Timestamp.class, id).toInstant();
    }

    void age(String id, Instant value) {
        db.update("UPDATE game_saves SET last_activity_at=? WHERE id=?", Timestamp.from(value), id);
    }

    @Test
    void ownerDeletesAllLeagueDataWhileOtherLeaguesAndCatalogSurvive() {
        var owner = create(1);
        var other = create(1);
        var id = owner.room().id();
        var state = management.state(id, token(owner));
        management.update(
            id,
            token(owner),
            new UpdateTeamRequest(state.formation(), state.mentality(), state.lineup(), 0)
        );
        saves.ready(id, token(owner), true);
        var playerCount = db.queryForObject("SELECT COUNT(*) FROM catalog_players", Integer.class);
        saves.delete(id, token(owner));
        for (var table : List.of("save_lineups", "save_team_management", "save_members", "save_players", "save_teams"))
            assertThat(
                db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE save_id=?", Integer.class, id)
            ).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM game_saves WHERE id=?", Integer.class, id)).isZero();
        assertThat(saves.room(other.room().id()).id()).isEqualTo(other.room().id());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_players", Integer.class)).isEqualTo(playerCount);
    }

    @Test
    void onlyOwnerCanDeleteAndUnauthenticatedAttemptsDoNotRefreshActivity() {
        var owner = create(2);
        var other = create(1);
        var member = saves.join(
            owner.room().inviteCode(),
            new JoinLeagueRequest("Amigo", catalog.teams(null).get(1).id())
        );
        var old = NOW.minus(Duration.ofDays(20));
        age(owner.room().id(), old);
        assertThatThrownBy(() -> saves.delete(owner.room().id(), token(member)))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(403);
        assertThatThrownBy(() -> saves.delete(owner.room().id(), token(other)))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(401);
        assertThatThrownBy(() -> saves.delete(owner.room().id(), null))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(401);
        assertThat(activity(owner.room().id())).isEqualTo(old);
        assertThat(saves.room(owner.room().id()).members()).hasSize(2);
    }

    @Test
    void expiryIsStrictlyMoreThanSixtyDaysAndCandidatesAreRecheckedAfterActivity() {
        var expired = create(1);
        var exact = create(1);
        var refreshed = create(1);
        age(expired.room().id(), NOW.minus(Duration.ofDays(60)).minusSeconds(1));
        age(exact.room().id(), NOW.minus(Duration.ofDays(60)));
        age(refreshed.room().id(), NOW.minus(Duration.ofDays(70)));
        assertThat(lifecycle.expiredCandidates())
            .contains(expired.room().id(), refreshed.room().id())
            .doesNotContain(exact.room().id());
        saves.authorizedRoom(refreshed.room().id(), token(refreshed));
        assertThat(lifecycle.expire(refreshed.room().id())).isFalse();
        assertThat(lifecycle.expire(exact.room().id())).isFalse();
        assertThat(lifecycle.expire(expired.room().id())).isTrue();
        assertThat(lifecycle.expire(expired.room().id())).isFalse();
        assertThat(lifecycle.expiredCandidates()).isEmpty();
    }

    @Test
    void accountListingAndInvitePreviewsDoNotExtendRetentionButAnyMemberCanRenewIt() {
        String email = UUID.randomUUID() + "@example.com";
        registration.register(
            new RegistrationRequest("Criador", email, "SenhaTeste123!", "SenhaTeste123!", null, "Teste")
        );
        var account = auth.login(email, "SenhaTeste123!");
        var header = "Bearer " + account.accessToken();
        var owner = saves.createForAccount(
            new CreateLeagueRequest("Liga da conta", 2, "Criador", catalog.teams(null).getFirst().id()),
            header
        );
        var friend = saves.join(
            owner.room().inviteCode(),
            new JoinLeagueRequest("Amigo", catalog.teams(null).get(1).id())
        );
        var old = NOW.minus(Duration.ofDays(59));
        age(owner.room().id(), old);
        assertThat(saves.mine(header)).hasSize(1);
        saves.byCode(owner.room().inviteCode());
        saves.summary(owner.room().id(), token(friend));
        assertThat(activity(owner.room().id())).isEqualTo(old);
        var opened = saves.authorizedRoom(owner.room().id(), token(friend));
        assertThat(opened.lastActivityAt()).isEqualTo(NOW);
        assertThat(opened.inactivityExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(60)));
        saves.delete(owner.room().id(), header);
        assertThat(saves.mine(header)).isEmpty();
        assertThat(auth.require(header).id()).isEqualTo(account.id());
    }

    @Test
    void savedTacticsAndReadinessRenewTheLeague() {
        var owner = create(1);
        var id = owner.room().id();
        var state = management.state(id, token(owner));
        age(id, NOW.minus(Duration.ofDays(30)));
        management.update(
            id,
            token(owner),
            new UpdateTeamRequest(state.formation(), state.mentality(), state.lineup(), 0)
        );
        assertThat(activity(id)).isEqualTo(NOW);
        age(id, NOW.minus(Duration.ofDays(30)));
        saves.ready(id, token(owner), true);
        assertThat(activity(id)).isEqualTo(NOW);
    }

    @Test
    void existingRowsReceiveAFullGracePeriodOnlyOnce() {
        var source = new DriverManagerDataSource(
            "jdbc:h2:mem:retention-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        var isolated = new JdbcTemplate(source);
        isolated.execute(
            "CREATE TABLE game_saves(id VARCHAR(36) PRIMARY KEY,name VARCHAR(60),invite_code VARCHAR(12),capacity INTEGER,status VARCHAR(20),catalog_version VARCHAR(64),round_number INTEGER,created_at TIMESTAMP)"
        );
        isolated.update(
            "INSERT INTO game_saves VALUES ('legacy','Liga antiga','OLD',1,'LOBBY','old',1,?)",
            Timestamp.from(Instant.parse("2000-01-01T00:00:00Z"))
        );
        var before = Instant.now().minusSeconds(1);
        new ResourceDatabasePopulator(new ClassPathResource("init.db")).execute(source);
        assertThat(
            isolated
                .queryForObject("SELECT last_activity_at FROM game_saves WHERE id='legacy'", Timestamp.class)
                .toInstant()
        ).isAfter(before);
        var known = Instant.parse("2020-01-01T00:00:00Z");
        isolated.update("UPDATE game_saves SET last_activity_at=?", Timestamp.from(known));
        new ResourceDatabasePopulator(new ClassPathResource("init.db")).execute(source);
        assertThat(
            isolated
                .queryForObject("SELECT last_activity_at FROM game_saves WHERE id='legacy'", Timestamp.class)
                .toInstant()
        ).isEqualTo(known);
    }
}

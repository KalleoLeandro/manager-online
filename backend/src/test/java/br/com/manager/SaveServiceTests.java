package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.JoinLeagueRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.service.CatalogService;
import br.com.manager.service.SaveService;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SaveServiceTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    JdbcTemplate db;

    @Test
    void catalogHasThreeDivisionsPerCountryAndBoundedRatings() {
        assertThat(catalog.leagues()).hasSize(18);
        assertThat(catalog.teams(null)).hasSize(420);
        assertThat(
            catalog
                .leagues()
                .stream()
                .map(l -> l.country())
                .distinct()
        ).hasSize(6);
        assertThat(
            catalog
                .leagues()
                .stream()
                .map(l -> l.division())
                .distinct()
        ).containsExactlyInAnyOrder(1, 2, 3);
        var brazil = catalog.snapshot().rosterPolicies().getFirst();
        assertThat(brazil.leagueIds()).containsExactly(6L, 27L, 31L);
        assertThat(brazil.reference()).isEqualTo("POST_MID_YEAR_WINDOW");
        assertThat(brazil.windowEnd()).isEqualTo("2026-09-11");
        for (var league : catalog.snapshot().leagues()) {
            assertThat(
                catalog
                    .snapshot()
                    .teams()
                    .stream()
                    .filter(t -> t.leagueId().equals(league.id()))
                    .count()
            ).isEqualTo(league.selectable() ? league.teamCount() : 5L);
        }
        assertThat(catalog.snapshot().players()).allSatisfy(p -> {
            assertThat(p.ability()).isBetween(1, 99);
            assertThat(p.value1()).isBetween(1, 99);
            assertThat(p.value2()).isBetween(1, 99);
            if (p.generated()) {
                assertThat(p.photoUrl()).isEqualTo("/api/players/" + p.id() + "/portrait");
                assertThat(p.sourceUrl()).isNull();
                assertThat(p.generationOrigin()).isEqualTo("SQUAD_FILL");
            }
        });
    }

    private LeagueAccessResponse create(int capacity) {
        return saves.create(
            new CreateLeagueRequest("Save de teste", capacity, "Criador", catalog.teams(null).getFirst().id())
        );
    }

    @Test
    @Transactional
    void creatorChoosesFirstAndSnapshotIncludesInternationalClubs() {
        var access = create(2);
        var id = access.room().id();
        assertThat(
            db.queryForObject("SELECT reference FROM save_roster_policies WHERE save_id=?", String.class, id)
        ).isEqualTo("POST_MID_YEAR_WINDOW");
        assertThat(access.room().members()).hasSize(1);
        assertThat(access.room().members().getFirst().owner()).isTrue();
        assertThat(access.room().inviteCode()).hasSize(8);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM save_players WHERE save_id=?", Integer.class, id)).isEqualTo(
            catalog.snapshot().players().size()
        );
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM save_players WHERE save_id=? AND generated=TRUE", Integer.class, id)
        ).isEqualTo(
            (int) catalog
                .snapshot()
                .players()
                .stream()
                .filter(p -> p.generated())
                .count()
        );
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM save_teams WHERE save_id=? AND selectable=FALSE", Integer.class, id)
        ).isPositive();
        assertThat(
            db.queryForObject("SELECT token_hash FROM save_members WHERE id=?", String.class, access.memberId())
        ).isNotEqualTo(access.accessToken());
    }

    @Test
    @Transactional
    void joinProtectsTeamsCapacityAndReadiness() {
        var owner = create(2);
        var code = owner.room().inviteCode();
        var id = owner.room().id();
        assertThatThrownBy(() ->
            saves.join(code, new JoinLeagueRequest("Amigo", owner.room().members().getFirst().teamId()))
        )
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(409);
        var friend = saves.join(code, new JoinLeagueRequest("Amigo", catalog.teams(null).get(1).id()));
        assertThat(friend.room().members()).hasSize(2);
        assertThatThrownBy(() -> saves.join(code, new JoinLeagueRequest("Terceiro", catalog.teams(null).get(2).id())))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(409);
        assertThat(saves.ready(id, "Bearer " + owner.accessToken(), true).status()).isEqualTo("LOBBY");
        assertThat(saves.ready(id, "Bearer " + friend.accessToken(), true).status()).isEqualTo("READY");
        assertThat(saves.ready(id, "Bearer " + friend.accessToken(), false).status()).isEqualTo("LOBBY");
    }

    @Test
    @Transactional
    void soloSaveNeedsOnlyItsOwnConfirmationAndDoesNotAcceptOtherPlayers() {
        var solo = create(1);
        var id = solo.room().id();
        var token = "Bearer " + solo.accessToken();
        assertThat(solo.room().members()).hasSize(1);
        assertThat(saves.ready(id, token, true).status()).isEqualTo("READY");
        assertThatThrownBy(() ->
            saves.join(
                solo.room().inviteCode(),
                new JoinLeagueRequest("Outro treinador", catalog.teams(null).get(1).id())
            )
        )
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(409);
        assertThat(saves.ready(id, token, false).status()).isEqualTo("LOBBY");
    }

    @Test
    @Transactional
    void savesAreIndependentFromCatalogAndEachOther() {
        var first = create(1);
        var second = create(1);
        var player = catalog.snapshot().players().getFirst();
        db.update("UPDATE catalog_players SET ability=1 WHERE id=?", player.id());
        db.update("UPDATE save_players SET ability=2 WHERE save_id=? AND player_id=?", first.room().id(), player.id());
        assertThat(
            db.queryForObject(
                "SELECT ability FROM save_players WHERE save_id=? AND player_id=?",
                Integer.class,
                second.room().id(),
                player.id()
            )
        ).isEqualTo(player.ability());
    }

    @Test
    @Transactional
    void accessIsScopedToSaveAndInternationalTeamsCannotBeChosen() {
        var first = create(1);
        var second = create(1);
        assertThatThrownBy(() -> saves.authorizedRoom(first.room().id(), "Bearer " + second.accessToken()))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(401);
        var international = catalog
            .snapshot()
            .teams()
            .stream()
            .filter(t -> !t.selectable())
            .findFirst()
            .orElseThrow();
        assertThatThrownBy(() -> saves.create(new CreateLeagueRequest("Inválido", 2, "Treinador", international.id())))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(400);
    }

    @Test
    void simultaneousJoinsCannotClaimSameClub() throws Exception {
        var owner = create(3);
        var room = owner.room();
        var team = catalog.teams(null).get(1).id();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> join = () -> {
                gate.await();
                try {
                    saves.join(room.inviteCode(), new JoinLeagueRequest("Amigo", team));
                    return true;
                } catch (DefaultErrorException e) {
                    if (e.getStatusCode().value() != 409) throw e;
                    return false;
                }
            };
            var a = executor.submit(join);
            var b = executor.submit(join);
            gate.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(
                true,
                false
            );
            assertThat(saves.byCode(room.inviteCode()).members()).hasSize(2);
        } finally {
            db.update("DELETE FROM save_members WHERE save_id=?", room.id());
            db.update("DELETE FROM save_players WHERE save_id=?", room.id());
            db.update("DELETE FROM save_teams WHERE save_id=?", room.id());
            db.update("DELETE FROM save_roster_policy_leagues WHERE save_id=?", room.id());
            db.update("DELETE FROM save_roster_policies WHERE save_id=?", room.id());
            db.update("DELETE FROM game_saves WHERE id=?", room.id());
        }
    }
}

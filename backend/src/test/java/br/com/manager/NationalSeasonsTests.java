package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NationalSeasonsTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    NationalChampionshipService championships;

    @Autowired
    LeagueCalendarService calendar;

    @Autowired
    JdbcTemplate db;

    @Test
    void createsFullDrawAndSharesItWithAnotherManagerInSameCompetition() {
        var teams = catalog.teams(null);
        var home = teams.getFirst();
        var other = teams
            .stream()
            .filter(t -> t.leagueId().equals(home.leagueId()) && !t.id().equals(home.id()))
            .findFirst()
            .orElseThrow();
        var access = saves.create(new CreateLeagueRequest("Sorteio", 2, "Treinador", home.id()));
        var friend = saves.join(access.room().inviteCode(), new JoinLeagueRequest("Amigo", other.id()));
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var result = championships.championship(id, token, null);
        int n = result.season().teamCount();
        assertThat(result.fixtures()).hasSize(n * (n - 1));
        assertThat(result.season().rounds()).isEqualTo((n - 1) * 2);
        assertThat(result.fixtures()).allMatch(
            f -> f.homeGoals() == null && f.awayGoals() == null && f.status().equals("SCHEDULED")
        );
        assertThat(championships.championship(id, "Bearer " + friend.accessToken(), null).fixtures()).isEqualTo(
            result.fixtures()
        );
        assertThat(championships.championship(id, token, null).season().id()).isEqualTo(result.season().id());
        assertThatThrownBy(() -> championships.championship(id, null, null)).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void advancingYearCreatesNewDrawAndKeepsPreviousSeasonAndSnapshots() {
        var access = saves.create(new CreateLeagueRequest("Anos", 1, "Treinador", catalog.teams(null).getFirst().id()));
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var first = championships.championship(id, token, 2026);
        db.update("UPDATE catalog_teams SET name='Nome alterado no catálogo' WHERE id=?", first.ownTeamId());
        int day = (int) ChronoUnit.DAYS.between(LocalDate.of(2026, 7, 1), LocalDate.of(2027, 1, 1)) + 1;
        db.update("UPDATE game_saves SET round_number=? WHERE id=?", day, id);
        calendar.week(id, token);
        var next = championships.championship(id, token, 2027);
        assertThat(next.season().id()).isNotEqualTo(first.season().id());
        assertThat(next.season().label()).isEqualTo("2027/2028");
        assertThat(championships.championship(id, token, 2026)).isEqualTo(
            new br.com.manager.models.responses.NationalChampionshipResponse(
                first.ownTeamId(),
                first.season(),
                next.seasons(),
                first.fixtures()
            )
        );
        assertThat(next.fixtures()).allMatch(
            f ->
                !f.homeTeamName().equals("Nome alterado no catálogo") &&
                !f.awayTeamName().equals("Nome alterado no catálogo")
        );
        assertThat(championships.championship(id, token, null).season().year()).isEqualTo(2026);
        saves.delete(id, token);
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM national_seasons WHERE save_id=?", Integer.class, id)
        ).isZero();
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM national_fixtures WHERE save_id=?", Integer.class, id)
        ).isZero();
    }

    @Test
    void brazilianSeasonUsesCalendarYearAndMatchesAppearOnWeeklyCalendar() {
        var team = catalog
            .teams(null)
            .stream()
            .filter(t -> t.leagueId().equals(6L))
            .findFirst()
            .orElseThrow();
        var access = saves.create(new CreateLeagueRequest("Brasileirão", 1, "Treinador", team.id()));
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var result = championships.championship(id, token, null);
        assertThat(result.season().label()).isEqualTo("2026");
        assertThat(result.season().firstDate()).isEqualTo(LocalDate.of(2026, 1, 28));
        assertThat(result.season().lastDate()).isEqualTo(LocalDate.of(2026, 12, 2));
        var initial = db.queryForObject("SELECT calendar_start_date FROM game_saves WHERE id=?", LocalDate.class, id);
        assertThat(initial).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.fixtures()).hasSize(380);
        var match = result
            .fixtures()
            .stream()
            .filter(f -> f.homeTeamId().equals(team.id()) || f.awayTeamId().equals(team.id()))
            .findFirst()
            .orElseThrow();
        int day = (int) ChronoUnit.DAYS.between(initial, match.date()) + 1;
        db.update("UPDATE game_saves SET round_number=? WHERE id=?", day, id);
        assertThat(calendar.week(id, token).events()).anyMatch(
            e -> e.type().equals("MATCH") && e.title().contains(team.name())
        );
        for (boolean home : java.util.List.of(true, false)) {
            var fixture = result
                .fixtures()
                .stream()
                .filter(f -> home ? f.homeTeamId().equals(team.id()) : f.awayTeamId().equals(team.id()))
                .findFirst()
                .orElseThrow();
            int fixtureDay = (int) ChronoUnit.DAYS.between(initial, fixture.date()) + 1;
            db.update("UPDATE game_saves SET round_number=? WHERE id=?", fixtureDay, id);
            var event = calendar
                .week(id, token)
                .events()
                .stream()
                .filter(e -> e.id().equals("national-" + fixture.id()))
                .findFirst()
                .orElseThrow();
            assertThat(event.opponentTeamId()).isEqualTo(home ? fixture.awayTeamId() : fixture.homeTeamId());
            assertThat(event.opponentTeamName()).isEqualTo(home ? fixture.awayTeamName() : fixture.homeTeamName());
            assertThat(event.venue()).isEqualTo(home ? "C" : "V");
        }
        assertThat(result.fixtures()).allMatch(f -> f.date().getYear() == 2026);
    }

    @Test
    void joiningBrazilToAnUnstartedEuropeanRoomMovesItsStartToJanuary() {
        var european = saves.create(
            new CreateLeagueRequest("Mixed calendar", 2, "European", catalog.teams(1L).getFirst().id())
        );
        var brazilian = saves.join(
            european.room().inviteCode(),
            new JoinLeagueRequest("Brazilian", catalog.teams(6L).getFirst().id())
        );
        var id = european.room().id();
        assertThat(
            db.queryForObject("SELECT calendar_start_date FROM game_saves WHERE id=?", LocalDate.class, id)
        ).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(
            championships
                .championship(id, "Bearer " + brazilian.accessToken(), null)
                .season()
                .firstDate()
        ).isEqualTo(LocalDate.of(2026, 1, 28));
        assertThat(
            championships
                .championship(id, "Bearer " + european.accessToken(), null)
                .season()
                .firstDate()
        ).isEqualTo(LocalDate.of(2026, 8, 1));
    }
}

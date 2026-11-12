package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.PlayerAwardDtos.*;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.repository.PlayerAwardRepository;
import br.com.manager.service.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayerAwardsTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    PlayerAwardService awards;

    @Autowired
    PlayerAwardQueryService queries;

    @Autowired
    PlayerAwardRepository repository;

    @Autowired
    JdbcTemplate db;

    private LeagueAccessResponse create() {
        return saves.create(
            new CreateLeagueRequest("Premiações de teste", 1, "Treinador Prêmios", catalog.teams(null).getFirst().id())
        );
    }

    private String token(LeagueAccessResponse a) {
        return "Bearer " + a.accessToken();
    }

    private List<MatchStateDto> season(LeagueAccessResponse a) {
        String id = a.room().id();
        long team = a.room().members().getFirst().teamId();
        var ids = db.queryForList(
            "SELECT player_id FROM save_players WHERE save_id=? AND team_id=? ORDER BY player_id LIMIT 11",
            Long.class,
            id,
            team
        );
        var fixtures = db.queryForList(
            "SELECT id,season_id,home_team_id,away_team_id,match_date FROM national_fixtures WHERE save_id=? AND (home_team_id=? OR away_team_id=?) AND match_date<DATE '2027-01-01' ORDER BY match_date LIMIT 12",
            id,
            team,
            team
        );
        assertThat(fixtures).hasSize(12);
        db.update("UPDATE national_fixtures SET status='CANCELLED' WHERE save_id=?", id);
        var result = new ArrayList<MatchStateDto>();
        for (var f : fixtures) {
            var s = new MatchStateDto();
            s.fixtureId = ((Number) f.get("ID")).longValue();
            s.seasonId = ((Number) f.get("SEASON_ID")).longValue();
            s.home.id = ((Number) f.get("HOME_TEAM_ID")).longValue();
            s.away.id = ((Number) f.get("AWAY_TEAM_ID")).longValue();
            s.home.name = "Mandante";
            s.away.name = "Visitante";
            s.date = f.get("MATCH_DATE").toString();
            s.status = "FINISHED";
            var own = s.home.id == team ? s.home : s.away;
            own.goals = 3;
            for (int i = 0; i < ids.size(); i++) {
                var p = new MatchStateDto.Player();
                p.id = ids.get(i);
                p.name = "Atleta " + i;
                p.position = "ATA";
                p.ability = 85;
                p.entered = true;
                p.minutes = 90;
                p.rating = i == 0 ? 9.8 : i == 1 ? 8.0 : i == 2 ? 9.1 : 6.0;
                p.goals = i == 0 ? 1 : i == 1 ? 2 : 0;
                p.assists = i == 2 ? 2 : 0;
                p.passesCompleted = 30;
                own.players.add(p);
            }
            db.update(
                "UPDATE national_fixtures SET status='PLAYED',home_goals=?,away_goals=? WHERE id=?",
                s.home.goals,
                s.away.goals,
                s.fixtureId
            );
            awards.recordActual(id, s);
            result.add(s);
        }
        return result;
    }

    @Test
    void annualAndCompetitionAwardsAreImmutableAndGeneratedOnlyOnce() {
        var a = create();
        var states = season(a);
        String id = a.room().id();
        awards.synchronize(id, LocalDate.of(2027, 1, 1));
        var history = repository.history(id);
        assertThat(history).hasSize(3);
        assertThat(history.stream().map(Ceremony::key)).containsExactlyInAnyOrder(
            "GOLDEN_BALL",
            "GOLDEN_BOOT",
            "TOP_SCORER"
        );
        var ball = repository.find(
            id,
            history
                .stream()
                .filter(c -> c.key().equals("GOLDEN_BALL"))
                .findFirst()
                .orElseThrow()
                .id()
        );
        var boot = repository.find(
            id,
            history
                .stream()
                .filter(c -> c.key().equals("GOLDEN_BOOT"))
                .findFirst()
                .orElseThrow()
                .id()
        );
        assertThat(ball.finalists()).hasSize(3);
        assertThat(ball.finalists().getFirst().player().playerId()).isEqualTo(
            states.getFirst().home.players.isEmpty()
                ? states.getFirst().away.players.get(0).id
                : states.getFirst().home.players.get(0).id
        );
        assertThat(boot.finalists().getFirst().player().goals()).isEqualTo(24);
        assertThat(boot.finalists().getFirst().player().averageRating()).isEqualTo(8);
        db.update("UPDATE save_players SET ability=1 WHERE save_id=?", id);
        awards.synchronize(id, LocalDate.of(2027, 1, 2));
        assertThat(repository.history(id)).hasSize(3);
        assertThat(repository.find(id, ball.id())).isEqualTo(ball);
    }

    @Test
    void recordedMatchesAreNotCountedTwiceAndFriendliesCannotEarnAwards() {
        var a = create();
        var states = season(a);
        var first = states.getFirst();
        var before = repository.aggregate(a.room().id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        awards.recordActual(a.room().id(), first);
        assertThat(
            repository.aggregate(a.room().id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null)
        ).isEqualTo(before);
        first.fixtureId = -1;
        first.friendlyMatch = true;
        assertThatCode(() -> awards.recordActual(a.room().id(), first)).doesNotThrowAnyException();
        assertThat(
            repository.aggregate(a.room().id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null)
        ).isEqualTo(before);
    }

    @Test
    void aPendingGamePreventsPrematureYearlyAwards() {
        var a = create();
        var states = season(a);
        db.update("DELETE FROM player_match_performances WHERE fixture_id=?", states.getLast().fixtureId);
        db.update("DELETE FROM player_performance_coverage WHERE fixture_id=?", states.getLast().fixtureId);
        db.update(
            "UPDATE national_fixtures SET status='SCHEDULED',home_goals=NULL,away_goals=NULL WHERE id=?",
            states.getLast().fixtureId
        );
        awards.synchronize(a.room().id(), LocalDate.of(2027, 1, 1));
        assertThat(repository.history(a.room().id())).isEmpty();
    }

    @Test
    void ceremoniesAreIsolatedByLeagueAndSeenStateIsIdempotent() {
        var a = create();
        season(a);
        awards.synchronize(a.room().id(), LocalDate.of(2027, 1, 1));
        var pending = queries.pending(a.room().id(), token(a));
        assertThat(pending).hasSize(2);
        long award = pending.getFirst().id();
        var b = create();
        assertThatThrownBy(() -> queries.detail(b.room().id(), token(b), award)).isInstanceOf(
            DefaultErrorException.class
        );
        assertThatThrownBy(() -> queries.pending(a.room().id(), token(b))).isInstanceOf(DefaultErrorException.class);
        queries.acknowledge(a.room().id(), token(a), award);
        queries.acknowledge(a.room().id(), token(a), award);
        assertThat(queries.pending(a.room().id(), token(a))).hasSize(1);
        assertThat(queries.detail(a.room().id(), token(a), award).finalists()).hasSize(3);
    }
}

package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.requests.*;
import br.com.manager.repository.*;
import br.com.manager.service.*;
import br.com.manager.utils.*;
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
class WorldCompetitionTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    WorldCompetitionService world;

    @Autowired
    WorldCompetitionRepository repository;

    @Autowired
    JdbcTemplate db;

    @Autowired
    SaveRepository snapshots;

    @Test
    void catalogExpansionAddsOnlyNewClubsAndNeverRestoresAnExistingRoster() {
        var owner = saves.create(
            new CreateLeagueRequest("Expansion test", 1, "Owner", catalog.teams(1L).getFirst().id())
        );
        String id = owner.room().id();
        long team = owner.room().members().getFirst().teamId();
        long original = db.queryForObject(
            "SELECT MIN(player_id) FROM save_players WHERE save_id=? AND team_id=?",
            Long.class,
            id,
            team
        );
        db.update("UPDATE save_players SET name='League specific name' WHERE save_id=? AND player_id=?", id, original);
        db.update(
            "INSERT INTO catalog_teams(id,league_id,name,short_name,abbreviation,rating,national_rank,international_bonus,selectable) VALUES(990001,1,'New catalog club','New club','NEW',60,999,0,FALSE)"
        );
        db.update(
            "INSERT INTO catalog_players(id,team_id,name,position,level,ability,attribute1,value1,attribute2,value2,appearances,goals,assists,minutes,generated,generation_origin) VALUES(990001,990001,'New Player','GOL','Regular',60,'Reflexes',60,'Positioning',60,0,0,0,0,TRUE,'WORLD_ROSTER_GAP')"
        );
        snapshots.expandCatalog(id);
        snapshots.expandCatalog(id);
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM save_teams WHERE save_id=? AND team_id=990001", Integer.class, id)
        ).isOne();
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM save_players WHERE save_id=? AND player_id=990001",
                Integer.class,
                id
            )
        ).isOne();
        assertThat(
            db.queryForObject(
                "SELECT name FROM save_players WHERE save_id=? AND player_id=?",
                String.class,
                id,
                original
            )
        ).isEqualTo("League specific name");
    }

    @Test
    void unattendedChampionshipsExistAndAdvanceOnlyToTheSlowestHumansDate() {
        var owner = saves.create(new CreateLeagueRequest("World test", 2, "Owner", catalog.teams(1L).getFirst().id()));
        var id = owner.room().id();
        var own = owner.room().members().getFirst().teamId();
        var foreign = catalog.teams(2L).getFirst();
        var initial = world.overview(id);
        assertThat(initial.competitions())
            .extracting(c -> c.leagueId())
            .contains(1L, 2L, 3L, 4L, 5L, 6L);
        long fixture = db.queryForObject(
            "SELECT MIN(id) FROM national_fixtures WHERE save_id=? AND season_id IN(SELECT id FROM national_seasons WHERE save_id=? AND league_id=2)",
            Long.class,
            id,
            id
        );
        db.update("UPDATE national_fixtures SET match_date=? WHERE id=?", initial.date().plusDays(2), fixture);
        var friend = saves.join(
            owner.room().inviteCode(),
            new JoinLeagueRequest("Friend", catalog.teams(1L).get(1).id())
        );
        long other = friend
            .room()
            .members()
            .stream()
            .filter(m -> !m.teamId().equals(own))
            .findFirst()
            .orElseThrow()
            .teamId();
        db.update("UPDATE save_teams SET calendar_day=1 WHERE save_id=?", id);
        db.update("UPDATE save_teams SET calendar_day=10 WHERE save_id=? AND team_id=?", id, own);
        world.synchronize(id);
        assertThat(
            db.queryForObject("SELECT status FROM national_fixtures WHERE id=?", String.class, fixture)
        ).isEqualTo("SCHEDULED");
        db.update("UPDATE save_teams SET calendar_day=4 WHERE save_id=? AND team_id=?", id, other);
        world.synchronize(id);
        assertThat(
            db.queryForObject("SELECT status FROM national_fixtures WHERE id=?", String.class, fixture)
        ).isEqualTo("PLAYED");
        var score = db.queryForMap("SELECT home_goals,away_goals FROM national_fixtures WHERE id=?", fixture);
        world.synchronize(id);
        assertThat(db.queryForMap("SELECT home_goals,away_goals FROM national_fixtures WHERE id=?", fixture)).isEqualTo(
            score
        );
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=? AND s.league_id=1 AND f.status='PLAYED'",
                Integer.class,
                id
            )
        ).isZero();
        var season = initial
            .competitions()
            .stream()
            .filter(c -> c.leagueId() == 2)
            .findFirst()
            .orElseThrow();
        assertThat(world.championship(id, season.id()).table()).anyMatch(t -> t.played() > 0);
    }

    @Test
    void namesAreReproducibleDistinctAndLocalizedForLargeFutureIntakes() {
        for (var country : List.of("Brasil", "Inglaterra", "Espanha", "Itália", "França", "Alemanha")) {
            var names = new HashSet<String>();
            var random = new Random(7);
            var duplicateRun = new HashSet<String>();
            var sameRandom = new Random(7);
            for (int i = 0; i < 1000; i++) assertThat(PlayerNameGenerator.next(country, random, names))
                .isEqualTo(PlayerNameGenerator.next(country, sameRandom, duplicateRun))
                .doesNotContain("0", "1", "2");
            assertThat(names).hasSize(1000);
        }
    }

    @Test
    void realEuropeanBaselineIsFrozenAndUnavailableForHumanChampionships() {
        db.update(
            "INSERT INTO historical_seasons(league_id,season_year,label,name,country,first_date,last_date,source_url) VALUES(1,2024,'2024/2025','Synthetic historical league','Inglaterra',DATE '2024-08-01',DATE '2025-05-31','https://example.test/history')"
        );
        long template = db.queryForObject(
            "SELECT id FROM historical_seasons WHERE league_id=1 AND season_year=2024",
            Long.class
        );
        var english = catalog.teams(1L);
        for (int i = 0; i < 2; i++) db.update(
            "INSERT INTO historical_standings(season_id,team_id,team_name,position,played,wins,draws,losses,goals_for,goals_against,points) VALUES(?,?,?,?,1,?,0,?,?,?,?)",
            template,
            english.get(i).id(),
            "Historical club " + i,
            i + 1,
            i == 0 ? 1 : 0,
            i == 0 ? 0 : 1,
            i == 0 ? 2 : 1,
            i == 0 ? 1 : 2,
            i == 0 ? 3 : 0
        );
        var owner = saves.create(
            new CreateLeagueRequest("Historical test", 2, "Brazilian", catalog.teams(6L).getFirst().id())
        );
        String id = owner.room().id();
        var baseline = world
            .overview(id)
            .competitions()
            .stream()
            .filter(c -> c.historical())
            .findFirst()
            .orElseThrow();
        assertThat(baseline.year()).isEqualTo(2024);
        assertThat(world.championship(id, baseline.id()).table().getFirst().points()).isEqualTo(3);
        assertThat(world.championship(id, baseline.id()).fixtures()).isEmpty();
        db.update("UPDATE historical_standings SET points=99 WHERE season_id=?", template);
        assertThat(world.championship(id, baseline.id()).table().getFirst().points()).isEqualTo(3);
        saves.join(owner.room().inviteCode(), new JoinLeagueRequest("European", english.getFirst().id()));
        assertThat(world.overview(id).competitions()).noneMatch(c -> c.historical() && c.leagueId() == 1);
        assertThatThrownBy(() -> world.championship(id, baseline.id())).isInstanceOf(
            br.com.manager.exception.DefaultErrorException.class
        );
    }
}

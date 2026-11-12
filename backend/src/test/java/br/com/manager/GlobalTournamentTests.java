package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.models.requests.CreateLeagueRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GlobalTournamentTests {

    @Autowired
    SaveService saves;

    @Autowired
    SaveRepository leagues;

    @Autowired
    CatalogService catalog;

    @Autowired
    TournamentService tournaments;

    @Autowired
    TournamentRepository repository;

    @Autowired
    PlayerAwardService playerAwards;

    @Autowired
    JdbcTemplate db;

    @MockitoBean
    TournamentParticipationService participation;

    String league() {
        var clubs = catalog.teams(null);
        var access = saves.create(new CreateLeagueRequest("Torneios globais", 1, "Teste Global", clubs.getLast().id()));
        leagues.expandCatalog(access.room().id());
        return access.room().id();
    }

    @Test
    void intercontinentalUsesBothBranchesAndAwardsFourDistinctTrophiesExactlyOnce() {
        String id = league();
        var clubs = catalog.teams(null);
        var rules = TournamentRulebooks.intercontinental(2026);
        var entries = new ArrayList<Entrant>();
        String[] stages = { "PLAYOFF", "PLAYOFF", "AFRICA_ASIA_PACIFIC", "AMERICAS", "AMERICAS", "FINAL" };
        for (int i = 0; i < 6; i++) entries.add(
            new Entrant(clubs.get(i).id(), clubs.get(i).name(), stages[i], i + 1, "Association " + i, null, 1, 70 + i)
        );
        when(participation.entries(anyString(), any())).thenReturn(List.of());
        when(participation.entries(eq(id), eq(rules))).thenReturn(entries);
        tournaments.synchronize(id, LocalDate.of(2026, 9, 19));
        var edition = repository
            .editions(id)
            .stream()
            .filter(e -> e.key().equals("INTERCONTINENTAL"))
            .findFirst()
            .orElseThrow();
        assertThat(edition.currentStage()).isEqualTo(2);
        assertThat(repository.games(id, edition.id())).hasSize(2);
        var africa = repository.ties(edition.id(), 1).getFirst().winner();
        tournaments.synchronize(id, LocalDate.of(2026, 12, 16));
        edition = repository
            .editions(id)
            .stream()
            .filter(e -> e.id() == editionId(id))
            .findFirst()
            .orElseThrow();
        assertThat(edition.status()).isEqualTo("FINISHED");
        assertThat(repository.games(id, edition.id()))
            .hasSize(5)
            .allMatch(g -> g.status().equals("PLAYED"));
        assertThat(repository.ties(edition.id(), 3).getFirst()).matches(t -> t.home() == africa || t.away() == africa);
        assertThat(
            db.queryForList(
                "SELECT trophy_id FROM tournament_awards WHERE edition_id=? ORDER BY trophy_id",
                Integer.class,
                edition.id()
            )
        ).containsExactly(32, 34, 35, 36);
        tournaments.synchronize(id, LocalDate.of(2026, 12, 17));
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM tournament_awards WHERE edition_id=?", Integer.class, edition.id())
        ).isEqualTo(4);
        playerAwards.synchronize(id, LocalDate.of(2026, 12, 17));
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM player_awards WHERE save_id=? AND scope_key=? AND award_key='TOP_SCORER'",
                Integer.class,
                id,
                "T:" + edition.id()
            )
        ).isEqualTo(1);
    }

    long editionId(String id) {
        return repository
            .editions(id)
            .stream()
            .filter(e -> e.key().equals("INTERCONTINENTAL"))
            .findFirst()
            .orElseThrow()
            .id();
    }

    @Test
    void worldCupRunsOnlyEveryFourthYearHasFortyEightGroupGamesAndSixtyThreeTotal() {
        String id = league();
        var clubs = catalog.teams(null);
        var entries = new ArrayList<Entrant>();
        int index = 0;
        for (var allocation : List.of(
            Map.entry("UEFA", 12),
            Map.entry("CONMEBOL", 6),
            Map.entry("AFC", 4),
            Map.entry("CAF", 4),
            Map.entry("CONCACAF", 5),
            Map.entry("OFC", 1)
        )) {
            for (int n = 0; n < allocation.getValue(); n++) {
                var c = clubs.get(index++);
                entries.add(
                    new Entrant(
                        c.id(),
                        c.name(),
                        "GROUPS",
                        index,
                        allocation.getKey() + n / 2,
                        allocation.getKey(),
                        1,
                        75
                    )
                );
            }
        }
        when(participation.entries(anyString(), any())).thenReturn(List.of());
        when(participation.entries(eq(id), eq(TournamentRulebooks.clubWorldCup(2029)))).thenReturn(entries);
        tournaments.synchronize(id, LocalDate.of(2028, 12, 31));
        assertThat(repository.editions(id))
            .filteredOn(e -> e.key().equals("CLUB_WORLD_CUP"))
            .hasSize(1);
        var scheduled = repository
            .editions(id)
            .stream()
            .filter(e -> e.key().equals("CLUB_WORLD_CUP"))
            .findFirst()
            .orElseThrow();
        assertThat(repository.games(id, scheduled.id())).allMatch(
            g -> g.status().equals("SCHEDULED") && g.date().getYear() == 2029
        );
        tournaments.synchronize(id, LocalDate.of(2029, 7, 13));
        var edition = repository
            .editions(id)
            .stream()
            .filter(e -> e.key().equals("CLUB_WORLD_CUP"))
            .findFirst()
            .orElseThrow();
        assertThat(edition.status()).isEqualTo("FINISHED");
        var games = repository.games(id, edition.id());
        assertThat(games).hasSize(63);
        assertThat(games.stream().filter(g -> g.stage() == 0)).hasSize(48);
        assertThat(repository.stageGroups(edition.id(), 0).values()).containsOnly(
            "Grupo A",
            "Grupo B",
            "Grupo C",
            "Grupo D",
            "Grupo E",
            "Grupo F",
            "Grupo G",
            "Grupo H"
        );
        assertThat(
            db.queryForObject("SELECT trophy_id FROM tournament_awards WHERE edition_id=?", Integer.class, edition.id())
        ).isEqualTo(33);
    }

    @Test
    void allSixNationalCupsAndBothContinentalRulebooksValidateAndRepeat() {
        for (int year : List.of(2026, 2027, 2028, 2029))
            for (var rules : tournaments.rules(year))
                assertThatCode(() -> TournamentRulebooks.validate(rules))
                    .as(rules.key() + year)
                    .doesNotThrowAnyException();
        assertThat(tournaments.rules(2026).stream().map(TournamentRulebooks.Rulebook::key))
            .contains(
                "BRA_CUP",
                "ENG_CUP",
                "ESP_CUP",
                "ITA_CUP",
                "FRA_CUP",
                "GER_CUP",
                "CHAMPIONS",
                "LIBERTADORES",
                "INTERCONTINENTAL"
            )
            .doesNotContain("CLUB_WORLD_CUP");
    }
}

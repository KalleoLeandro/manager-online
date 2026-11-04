package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.RosterPolicyDto;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.repository.ClubPaletteRepository;
import br.com.manager.repository.RosterPolicyRepository;
import br.com.manager.service.CatalogBootstrapService;
import br.com.manager.service.CatalogService;
import br.com.manager.service.SaveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogDatabaseTests {

    @Test
    void databaseGeneratesNumericPlayerIdsAndBootstrapKeepsThemStable() throws Exception {
        var previousMax = db.queryForObject("SELECT MAX(id) FROM catalog_players", Long.class);
        db.update(
            """
            INSERT INTO catalog_players (team_id,name,position,level,ability,attribute1,value1,attribute2,value2,appearances,goals,assists,minutes)
            SELECT team_id,'Novo atleta numérico',position,level,ability,attribute1,value1,attribute2,value2,0,0,0,0
            FROM catalog_players ORDER BY id LIMIT 1
            """
        );
        var generated = db.queryForObject(
            "SELECT id FROM catalog_players WHERE name='Novo atleta numérico'",
            Long.class
        );
        assertThat(generated).isGreaterThan(previousMax);
        bootstrap.run(new DefaultApplicationArguments());
        var player = catalog
            .players(null)
            .stream()
            .filter(p -> p.id().equals(generated))
            .findFirst()
            .orElseThrow();
        assertThat(player.photoUrl()).isEqualTo("/api/players/" + generated + "/portrait");
        assertThat(
            db.queryForObject("SELECT source_id FROM catalog_players WHERE id=?", String.class, generated)
        ).isNull();
    }

    @Test
    void catalogueKeysAndForeignKeysAreNumericAndSourceIdsRemainImportReferences() {
        assertThat(catalog.snapshot().leagues()).allSatisfy(l -> assertThat(l.id()).isPositive());
        assertThat(catalog.snapshot().teams()).allSatisfy(t -> {
            assertThat(t.id()).isPositive();
            assertThat(t.leagueId()).isPositive();
            assertThat(t.crestUrl()).isEqualTo("/api/teams/" + t.id() + "/crest");
        });
        assertThat(catalog.players(null)).allSatisfy(p -> {
            assertThat(p.id()).isPositive();
            assertThat(p.teamId()).isPositive();
        });
        assertThat(
            db.queryForObject("SELECT id FROM catalog_leagues WHERE source_id='bra.1'", Long.class)
        ).isPositive();
        for (var table : java.util.List.of(
            "catalog_leagues",
            "catalog_teams",
            "catalog_players",
            "catalog_roster_policies"
        )) {
            assertThat(
                db.queryForObject(
                    "SELECT data_type FROM information_schema.columns WHERE LOWER(table_name)=? AND LOWER(column_name)='id'",
                    String.class,
                    table
                )
            ).isEqualToIgnoringCase("BIGINT");
        }
    }

    @Test
    void namedParametersPreserveNullablePolicyDatesAndText() {
        var policy = new RosterPolicyDto(
            "País d'Ávila :param",
            "2026",
            java.util.List.of(6L),
            null,
            null,
            null,
            null,
            null,
            "Texto com :saveId e 'aspas'"
        );
        policies.importCatalog(java.util.List.of(policy));
        assertThat(policies.catalog()).contains(policy);
    }

    @Autowired
    CatalogService catalog;

    @Autowired
    ClubPaletteRepository palettes;

    @Autowired
    RosterPolicyRepository policies;

    @Autowired
    CatalogBootstrapService bootstrap;

    @Autowired
    SaveService saves;

    @Autowired
    JdbcTemplate db;

    @Test
    void catalogueAndPalettesReadDatabaseChangesWithoutRestartOrJson() throws Exception {
        var team = catalog.teams(null).getFirst();
        var player = catalog.players(team.id()).getFirst();
        db.update("UPDATE catalog_players SET name=? WHERE id=?", "Atleta alterado no banco", player.id());
        db.update("UPDATE catalog_team_palettes SET primary_color=? WHERE team_id=?", "#006633", team.id());
        bootstrap.run(new DefaultApplicationArguments());
        assertThat(
            catalog
                .players(team.id())
                .stream()
                .filter(p -> p.id().equals(player.id()))
                .findFirst()
                .orElseThrow()
                .name()
        ).isEqualTo("Atleta alterado no banco");
        assertThat(palettes.forTeam(team.id()).primary()).isEqualTo("#006633");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_metadata", Integer.class)).isEqualTo(1);
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='catalog_imports'",
                Integer.class
            )
        ).isZero();
    }

    @Test
    void leaguePolicySnapshotsSurviveLaterChangesToTheSharedCatalogue() {
        var league = saves.create(
            new CreateLeagueRequest("Políticas", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        db.update("UPDATE catalog_roster_policies SET window_end=DATE '2026-09-20'");
        assertThat(policies.forSave(league.room().id()).getFirst().windowEnd()).isEqualTo("2026-09-11");
        assertThat(catalog.catalog().rosterPolicies().getFirst().windowEnd()).isEqualTo("2026-09-20");
    }
}

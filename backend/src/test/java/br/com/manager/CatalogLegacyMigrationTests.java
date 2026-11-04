package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.RosterPolicyDto;
import br.com.manager.repository.CatalogMigrationRepository;
import br.com.manager.repository.RosterPolicyRepository;
import br.com.manager.service.CatalogBootstrapService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class CatalogLegacyMigrationTests {

    @Test
    void legacyMetadataAndLeaguePoliciesMigrateWithoutReplacingExistingPlayersOrProgress() throws Exception {
        var source = new DriverManagerDataSource(
            "jdbc:h2:mem:catalog-legacy-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        new ResourceDatabasePopulator(new ClassPathResource("init.db")).execute(source);
        var db = new JdbcTemplate(source);
        db.update(
            "INSERT INTO catalog_leagues (id,name,country,selectable,season,team_count) VALUES ('6','Liga','Brasil',TRUE,'2026',1)"
        );
        db.update(
            """
            INSERT INTO catalog_teams (id,league_id,name,short_name,abbreviation,rating,national_rank,international_bonus,selectable)
            VALUES ('98','6','Palmeiras','Palmeiras','PAL',77,1,0,TRUE)
            """
        );
        db.update(
            """
            INSERT INTO catalog_players (id,team_id,name,position,level,ability,attribute1,value1,attribute2,value2,appearances,goals,assists,minutes)
            VALUES ('999999','98','Nome preservado','GOL','Titular',81,'Reflexos',81,'Defesa',82,0,0,0,0)
            """
        );
        db.execute(
            "CREATE TABLE catalog_imports (version VARCHAR(64) PRIMARY KEY,source VARCHAR(200),rating_method VARCHAR(1000),roster_policies_json VARCHAR(5000))"
        );
        db.execute("ALTER TABLE game_saves ADD COLUMN roster_policies_json VARCHAR(5000) DEFAULT '[]'");
        var policy = new RosterPolicyDto(
            "Brasil",
            "2026",
            List.of(6L),
            "POST_MID_YEAR_WINDOW",
            "2026-07-20",
            "2026-09-11",
            "https://source.invalid",
            "REFERENCE",
            "Preservar"
        );
        var json = JsonMapper.builder().build().writeValueAsString(List.of(policy));
        db.update(
            "INSERT INTO catalog_imports VALUES (?,?,?,?)",
            "legacy-version",
            "Fonte anterior",
            "Método anterior",
            json
        );
        db.update(
            """
            INSERT INTO game_saves (id,name,invite_code,capacity,catalog_version,round_number,roster_policies_json)
            VALUES ('old-league','Liga existente','LEGACY01',1,'legacy-version',8,?)
            """,
            json
        );
        db.update(
            """
            INSERT INTO save_teams (save_id,team_id,league_id,name,short_name,abbreviation,rating,selectable,budget,points)
            VALUES ('old-league','98','6','Palmeiras','Palmeiras','PAL',74,TRUE,123456,17)
            """
        );
        var repository = new CatalogMigrationRepository(new NamedParameterJdbcTemplate(source), source);
        var policies = new RosterPolicyRepository(new NamedParameterJdbcTemplate(source));
        var bootstrap = new CatalogBootstrapService(
            repository,
            policies,
            new br.com.manager.service.PlayerProfilesService(
                new br.com.manager.repository.PlayerProfilesRepository(new NamedParameterJdbcTemplate(source)),
                repository
            ),
            true
        );
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            try {
                bootstrap.run(new DefaultApplicationArguments());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_players", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT name FROM catalog_players WHERE id='999999'", String.class)).isEqualTo(
            "Nome preservado"
        );
        assertThat(db.queryForObject("SELECT version FROM catalog_metadata", String.class)).isEqualTo("legacy-version");
        assertThat(
            db.queryForObject("SELECT points FROM save_teams WHERE save_id='old-league'", Integer.class)
        ).isEqualTo(17);
        assertThat(
            db.queryForObject("SELECT round_number FROM game_saves WHERE id='old-league'", Integer.class)
        ).isEqualTo(8);
        assertThat(policies.forSave("old-league")).containsExactly(policy);
        assertThat(repository.tableExists("catalog_imports")).isFalse();
        assertThat(repository.legacyPolicyColumn()).isFalse();
        db.update("UPDATE catalog_players SET ability=89 WHERE id='999999'");
        bootstrap.run(new DefaultApplicationArguments());
        assertThat(db.queryForObject("SELECT ability FROM catalog_players WHERE id='999999'", Integer.class)).isEqualTo(
            89
        );
    }
}

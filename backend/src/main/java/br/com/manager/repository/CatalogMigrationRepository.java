package br.com.manager.repository;

import br.com.manager.models.dto.CatalogMetadataDto;
import br.com.manager.models.dto.LegacyCatalogDto;
import br.com.manager.models.dto.LegacyLeaguePolicyDto;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogMigrationRepository {

    private static final RowMapper<LegacyCatalogDto> LEGACY_CATALOG_ROW_MAPPER = (r, n) ->
        new LegacyCatalogDto(
            new CatalogMetadataDto(r.getString("version"), r.getString("source"), r.getString("rating_method")),
            r.getString("roster_policies_json")
        );

    private static final RowMapper<LegacyLeaguePolicyDto> LEGACY_LEAGUE_POLICY_ROW_MAPPER = (r, n) ->
        new LegacyLeaguePolicyDto(r.getString("id"), r.getString("roster_policies_json"));

    private final NamedParameterJdbcTemplate db;
    private final DataSource dataSource;

    public CatalogMigrationRepository(NamedParameterJdbcTemplate db, DataSource dataSource) {
        this.db = db;
        this.dataSource = dataSource;
    }

    public boolean initialized() {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM catalog_metadata",
                new MapSqlParameterSource(),
                new SingleColumnRowMapper<>(Integer.class)
            ) > 0
        );
    }

    public boolean empty() {
        return (
            db.queryForObject(
                "SELECT (SELECT COUNT(*) FROM catalog_leagues)+(SELECT COUNT(*) FROM catalog_teams)+(SELECT COUNT(*) FROM catalog_players)",
                new MapSqlParameterSource(),
                new SingleColumnRowMapper<>(Integer.class)
            ) == 0
        );
    }

    public void requireNumericIds() {
        var type = db.queryForObject(
            "SELECT data_type FROM information_schema.columns WHERE LOWER(table_name)='catalog_players' AND LOWER(column_name)='id' AND table_schema=CURRENT_SCHEMA()",
            new MapSqlParameterSource(),
            new SingleColumnRowMapper<>(String.class)
        );
        if (!"BIGINT".equalsIgnoreCase(type)) throw new IllegalStateException(
            "O catálogo precisa usar IDs BIGINT antes de iniciar esta versão."
        );
    }

    public void executeSeed(String name) {
        var resource = new ClassPathResource("db/" + name);
        if (resource.exists()) new ResourceDatabasePopulator(resource).execute(dataSource);
    }

    public boolean tableExists(String table) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)=:tableName AND table_schema=CURRENT_SCHEMA()",
                new MapSqlParameterSource("tableName", table),
                new SingleColumnRowMapper<>(Integer.class)
            ) > 0
        );
    }

    public boolean legacyPolicyColumn() {
        return (
            db.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_name)=:tableName AND LOWER(column_name)=:columnName AND table_schema=CURRENT_SCHEMA()
                """,
                new MapSqlParameterSource()
                    .addValue("tableName", "game_saves")
                    .addValue("columnName", "roster_policies_json"),
                new SingleColumnRowMapper<>(Integer.class)
            ) > 0
        );
    }

    public Optional<LegacyCatalogDto> legacyCatalog() {
        if (!tableExists("catalog_imports")) return Optional.empty();
        return db
            .query(
                "SELECT * FROM catalog_imports ORDER BY version DESC",
                new MapSqlParameterSource(),
                LEGACY_CATALOG_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }

    public List<LegacyLeaguePolicyDto> legacySavePolicies() {
        if (!legacyPolicyColumn()) return List.of();
        return db.query(
            "SELECT id,roster_policies_json FROM game_saves WHERE roster_policies_json IS NOT NULL AND roster_policies_json<>:emptyPolicies",
            new MapSqlParameterSource("emptyPolicies", "[]"),
            LEGACY_LEAGUE_POLICY_ROW_MAPPER
        );
    }

    public void metadata(CatalogMetadataDto value) {
        db.update(
            "INSERT INTO catalog_metadata (id,version,source,rating_method) VALUES (1,:version,:source,:ratingMethod)",
            new MapSqlParameterSource()
                .addValue("version", value.version())
                .addValue("source", value.source())
                .addValue("ratingMethod", value.ratingMethod())
        );
    }

    public void removeLegacy(boolean dropPolicyColumn) {
        if (tableExists("catalog_imports")) db.execute(
            "DROP TABLE catalog_imports",
            new MapSqlParameterSource(),
            PreparedStatement::execute
        );
        if (dropPolicyColumn && legacyPolicyColumn()) db.execute(
            "ALTER TABLE game_saves DROP COLUMN roster_policies_json",
            new MapSqlParameterSource(),
            PreparedStatement::execute
        );
    }
}

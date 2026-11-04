package br.com.manager.repository;

import br.com.manager.models.dto.CatalogLeagueDto;
import br.com.manager.models.dto.CatalogMetadataDto;
import br.com.manager.models.dto.CatalogPlayerDto;
import br.com.manager.models.dto.CatalogTeamDto;
import br.com.manager.models.dto.PlayerTechnicalDto;
import br.com.manager.models.enums.PlayerAttribute;
import br.com.manager.utils.AssetUrls;
import br.com.manager.utils.EstimatedPlayerProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private static final RowMapper<CatalogLeagueDto> CATALOG_LEAGUE_ROW_MAPPER = (r, n) ->
        new CatalogLeagueDto(
            r.getLong("id"),
            r.getString("name"),
            r.getString("country"),
            r.getBoolean("selectable"),
            r.getString("season"),
            r.getInt("team_count"),
            r.getInt("division"),
            r.getString("source_url")
        );

    private static final RowMapper<CatalogMetadataDto> CATALOG_METADATA_ROW_MAPPER = (r, n) ->
        new CatalogMetadataDto(r.getString("version"), r.getString("source"), r.getString("rating_method"));

    private static final RowMapper<CatalogPlayerDto> CATALOG_PLAYER_ROW_MAPPER = (r, n) -> {
        var technical = technical(r);
        return new CatalogPlayerDto(
            r.getLong("id"),
            r.getLong("team_id"),
            r.getString("name"),
            r.getString("position"),
            r.getString("level"),
            r.getInt("ability"),
            r.getString("attribute1"),
            EstimatedPlayerProfile.specialty(technical, r.getString("attribute1"), r.getInt("value1")),
            r.getString("attribute2"),
            EstimatedPlayerProfile.specialty(technical, r.getString("attribute2"), r.getInt("value2")),
            r.getObject("age", Integer.class),
            r.getString("shirt_number"),
            AssetUrls.portrait(r.getLong("id")),
            r.getString("nationality"),
            r.getInt("appearances"),
            r.getInt("goals"),
            r.getInt("assists"),
            r.getInt("minutes"),
            r.getString("source_url"),
            r.getBoolean("generated"),
            r.getString("generation_origin"),
            technical
        );
    };

    private static final RowMapper<CatalogTeamDto> CATALOG_TEAM_ROW_MAPPER = (r, n) ->
        new CatalogTeamDto(
            r.getLong("id"),
            r.getLong("league_id"),
            r.getString("name"),
            r.getString("short_name"),
            r.getString("abbreviation"),
            AssetUrls.crest(r.getLong("id")),
            r.getString("color"),
            r.getInt("rating"),
            r.getInt("national_rank"),
            r.getDouble("international_bonus"),
            r.getBoolean("selectable"),
            r.getString("source_url"),
            r.getString("group_name")
        );

    private static PlayerTechnicalDto technical(ResultSet r) throws SQLException {
        if (r.getObject("profile_potential", Integer.class) == null) return EstimatedPlayerProfile.estimate(
            r.getLong("id"),
            r.getString("position"),
            r.getInt("ability"),
            r.getObject("age", Integer.class),
            r.getString("attribute1"),
            r.getInt("value1"),
            r.getString("attribute2"),
            r.getInt("value2")
        );
        var values = new LinkedHashMap<String, Integer>();
        var sourced = new ArrayList<String>();
        long mask = r.getLong("profile_source_mask");
        for (var attribute : PlayerAttribute.values()) {
            values.put(attribute.key(), r.getInt(attribute.key()));
            if ((mask & attribute.mask()) != 0) sourced.add(attribute.key());
        }
        return new PlayerTechnicalDto(
            Collections.unmodifiableMap(values),
            r.getInt("profile_potential"),
            List.copyOf(sourced),
            r.getString("profile_source_name"),
            r.getString("profile_source_url")
        );
    }

    private final NamedParameterJdbcTemplate db;

    public CatalogRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public CatalogMetadataDto metadata() {
        return db.queryForObject(
            "SELECT version,source,rating_method FROM catalog_metadata WHERE id=1",
            new MapSqlParameterSource(),
            CATALOG_METADATA_ROW_MAPPER
        );
    }

    public List<CatalogLeagueDto> leagues(boolean selectableOnly) {
        return db.query(
            "SELECT * FROM catalog_leagues" +
                (selectableOnly ? " WHERE selectable=TRUE" : "") +
                " ORDER BY display_order,id",
            new MapSqlParameterSource(),
            CATALOG_LEAGUE_ROW_MAPPER
        );
    }

    public List<CatalogTeamDto> teams(Long leagueId, boolean selectableOnly) {
        var conditions = new ArrayList<String>();
        var params = new MapSqlParameterSource();
        if (selectableOnly) conditions.add("selectable=TRUE");
        if (leagueId != null) {
            conditions.add("league_id=:leagueId");
            params.addValue("leagueId", leagueId);
        }
        var where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return db.query(
            "SELECT * FROM catalog_teams" + where + " ORDER BY display_order,id",
            params,
            CATALOG_TEAM_ROW_MAPPER
        );
    }

    public long internationalTeams() {
        return db.queryForObject(
            "SELECT COUNT(*) FROM catalog_teams WHERE selectable=FALSE",
            new MapSqlParameterSource(),
            new SingleColumnRowMapper<>(Long.class)
        );
    }

    public List<CatalogPlayerDto> players(Long teamId) {
        var filter = teamId == null ? "" : " WHERE p.team_id=:teamId";
        var params = new MapSqlParameterSource();
        if (teamId != null) params.addValue("teamId", teamId);
        return db.query(
            "SELECT p.*,pr.potential AS profile_potential,pr.source_mask AS profile_source_mask,pr.source_name AS profile_source_name,pr.source_url AS profile_source_url," +
                PlayerAttribute.selection("pr") +
                " FROM catalog_players p JOIN catalog_teams t ON t.id=p.team_id LEFT JOIN catalog_player_profiles pr ON pr.player_id=p.id" +
                filter +
                " ORDER BY t.display_order,p.ability DESC,p.name,p.id",
            params,
            CATALOG_PLAYER_ROW_MAPPER
        );
    }
}

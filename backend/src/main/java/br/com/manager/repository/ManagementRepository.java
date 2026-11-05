package br.com.manager.repository;

import br.com.manager.models.dto.LeaguePlayerSearchDto;
import br.com.manager.models.dto.ManagedPlayerDto;
import br.com.manager.models.dto.PlayerConditionDto;
import br.com.manager.models.dto.PlayerTechnicalDto;
import br.com.manager.models.dto.TeamSettingsDto;
import br.com.manager.models.dto.TeamSnapshotDto;
import br.com.manager.models.enums.PlayerAttribute;
import br.com.manager.utils.AssetUrls;
import br.com.manager.utils.EstimatedPlayerProfile;
import br.com.manager.utils.PlayerCondition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManagementRepository {

    private static final String SUSPENSION_SELECTION = """
    ,EXISTS(SELECT 1 FROM match_player_seasons ds WHERE ds.save_id=p.save_id AND ds.player_id=p.player_id
      AND ds.suspension_remaining>0 AND ds.season_id=(SELECT nf.season_id FROM national_fixtures nf
        WHERE nf.save_id=p.save_id AND (nf.home_team_id=p.team_id OR nf.away_team_id=p.team_id)
          AND nf.status='SCHEDULED' ORDER BY nf.match_date,nf.id LIMIT 1)) AS suspended
    """;

    private static final RowMapper<ManagedPlayerDto> MANAGED_PLAYER_ROW_MAPPER = (r, n) -> {
        var technical = technical(r);
        var condition = PlayerCondition.evaluate(
            r.getInt("ability"),
            r.getInt("fitness"),
            r.getInt("player_morale"),
            r.getInt("player_sharpness"),
            r.getString("injury_name"),
            r.getObject("return_round", Integer.class),
            r.getInt("round_number")
        );
        if (r.getBoolean("suspended")) condition = new PlayerConditionDto(
            condition.fitness(),
            0,
            false,
            "SUSPENDED",
            condition.injuryName(),
            condition.returnRound(),
            condition.morale(),
            condition.sharpness(),
            0
        );
        return new ManagedPlayerDto(
            r.getLong("player_id"),
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
            AssetUrls.portrait(r.getLong("player_id")),
            r.getInt("fitness"),
            r.getBoolean("generated"),
            r.getString("generation_origin"),
            r.getString("nationality"),
            technical,
            condition
        );
    };

    private static final RowMapper<TeamSettingsDto> TEAM_SETTINGS_ROW_MAPPER = (r, n) ->
        new TeamSettingsDto(
            r.getString("formation"),
            r.getString("mentality"),
            r.getInt("version"),
            tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readValue(r.getString("tactical_plan"), br.com.manager.models.dto.TacticalPlanDto.class)
        );

    private static final RowMapper<TeamSnapshotDto> TEAM_SNAPSHOT_ROW_MAPPER = (r, n) ->
        new TeamSnapshotDto(r.getLong("league_id"), r.getInt("rating"), r.getLong("budget"), r.getInt("points"));

    private static PlayerTechnicalDto technical(ResultSet r) throws SQLException {
        if (r.getObject("profile_potential", Integer.class) == null) return EstimatedPlayerProfile.estimate(
            r.getLong("player_id"),
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

    public ManagementRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public TeamSnapshotDto team(String id, Long teamId) {
        return db.queryForObject(
            "SELECT league_id,rating,budget,points FROM save_teams WHERE save_id=:saveId AND team_id=:teamId",
            new MapSqlParameterSource().addValue("saveId", id).addValue("teamId", teamId),
            TEAM_SNAPSHOT_ROW_MAPPER
        );
    }

    public List<ManagedPlayerDto> players(String id, Long teamId) {
        var params = new MapSqlParameterSource("saveId", id).addValue("teamId", teamId);
        var next = db.query(
            """
            SELECT CASE WHEN ns.tournament_id IS NULL THEN ns.id ELSE
                (SELECT MIN(root.id) FROM national_seasons root WHERE root.tournament_id=ns.tournament_id) END
            FROM national_fixtures nf JOIN national_seasons ns ON ns.id=nf.season_id
            WHERE nf.save_id=:saveId AND (nf.home_team_id=:teamId OR nf.away_team_id=:teamId)
                AND nf.status='SCHEDULED' ORDER BY nf.match_date,nf.id LIMIT 1
            """,
            params,
            (r, n) -> r.getLong(1)
        );
        params.addValue("disciplineSeason", next.isEmpty() ? null : next.getFirst(), java.sql.Types.BIGINT);
        return db.query(
            "SELECT p.*,pr.potential AS profile_potential,pr.source_mask AS profile_source_mask,pr.source_name AS profile_source_name,pr.source_url AS profile_source_url," +
                PlayerAttribute.selection("pr") +
                ",EXISTS(SELECT 1 FROM match_player_seasons ds WHERE ds.save_id=p.save_id AND ds.player_id=p.player_id AND ds.suspension_remaining>0 AND ds.season_id=:disciplineSeason) AS suspended" +
                ",COALESCE(pr.morale,100) AS player_morale,COALESCE(pr.sharpness,100) AS player_sharpness,pr.injury_name,pr.return_round,COALESCE(t.calendar_day,s.round_number) AS round_number FROM save_players p JOIN save_teams t ON t.save_id=p.save_id AND t.team_id=p.team_id JOIN game_saves s ON s.id=p.save_id LEFT JOIN save_player_profiles pr ON pr.save_id=p.save_id AND pr.player_id=p.player_id WHERE p.save_id=:saveId AND p.team_id=:teamId ORDER BY p.ability DESC,p.name",
            params,
            MANAGED_PLAYER_ROW_MAPPER
        );
    }

    public boolean introductionSeen(String id, String memberId) {
        return db.queryForObject(
            "SELECT introduction_seen FROM save_members WHERE save_id=:id AND id=:memberId",
            new MapSqlParameterSource("id", id).addValue("memberId", memberId),
            Boolean.class
        );
    }

    public void acknowledgeIntroduction(String id, String memberId) {
        db.update(
            "UPDATE save_members SET introduction_seen=TRUE WHERE save_id=:id AND id=:memberId",
            new MapSqlParameterSource("id", id).addValue("memberId", memberId)
        );
    }

    public List<LeaguePlayerSearchDto> search(String id, String query) {
        return db.query(
            "SELECT p.*,t.name AS team_name,pr.potential AS profile_potential,pr.source_mask AS profile_source_mask,pr.source_name AS profile_source_name,pr.source_url AS profile_source_url," +
                PlayerAttribute.selection("pr") +
                SUSPENSION_SELECTION +
                ",COALESCE(pr.morale,100) AS player_morale,COALESCE(pr.sharpness,100) AS player_sharpness,pr.injury_name,pr.return_round,COALESCE(t.calendar_day,s.round_number) AS round_number FROM save_players p JOIN save_teams t ON t.save_id=p.save_id AND t.team_id=p.team_id JOIN game_saves s ON s.id=p.save_id LEFT JOIN save_player_profiles pr ON pr.save_id=p.save_id AND pr.player_id=p.player_id WHERE p.save_id=:saveId AND LOWER(p.name) LIKE :query ESCAPE '!' ORDER BY p.ability DESC,p.name,p.player_id LIMIT 60",
            new MapSqlParameterSource("saveId", id).addValue(
                "query",
                "%" + query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
            ),
            (r, n) ->
                new LeaguePlayerSearchDto(
                    MANAGED_PLAYER_ROW_MAPPER.mapRow(r, n),
                    r.getLong("team_id"),
                    r.getString("team_name")
                )
        );
    }

    public Optional<TeamSettingsDto> settings(String id, Long teamId) {
        return db
            .query(
                "SELECT formation,mentality,version,tactical_plan FROM save_team_management WHERE save_id=:saveId AND team_id=:teamId",
                new MapSqlParameterSource().addValue("saveId", id).addValue("teamId", teamId),
                TEAM_SETTINGS_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }

    public List<Long> lineup(String id, Long teamId) {
        return db.query(
            "SELECT player_id FROM save_lineups WHERE save_id=:saveId AND team_id=:teamId ORDER BY slot_index",
            new MapSqlParameterSource().addValue("saveId", id).addValue("teamId", teamId),
            new SingleColumnRowMapper<>(Long.class)
        );
    }

    public Optional<String> leagueName(Long leagueId) {
        return db
            .query(
                "SELECT name FROM catalog_leagues WHERE id=:leagueId",
                new MapSqlParameterSource("leagueId", leagueId),
                new SingleColumnRowMapper<>(String.class)
            )
            .stream()
            .findFirst();
    }

    public void createSettings(String id, Long teamId, String formation, String mentality) {
        db.update(
            "INSERT INTO save_team_management (save_id,team_id,formation,mentality,version) VALUES (:saveId,:teamId,:formation,:mentality,1)",
            new MapSqlParameterSource()
                .addValue("saveId", id)
                .addValue("teamId", teamId)
                .addValue("formation", formation)
                .addValue("mentality", mentality)
        );
    }

    public void updateSettings(String id, Long teamId, String formation, String mentality) {
        db.update(
            "UPDATE save_team_management SET formation=:formation,mentality=:mentality,version=version+1 WHERE save_id=:saveId AND team_id=:teamId",
            new MapSqlParameterSource()
                .addValue("saveId", id)
                .addValue("teamId", teamId)
                .addValue("formation", formation)
                .addValue("mentality", mentality)
        );
    }

    public void savePlan(String id, Long teamId, br.com.manager.models.dto.TacticalPlanDto plan) {
        db.update(
            "UPDATE save_team_management SET tactical_plan=:plan WHERE save_id=:saveId AND team_id=:teamId",
            new MapSqlParameterSource("saveId", id)
                .addValue("teamId", teamId)
                .addValue("plan", tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(plan))
        );
    }

    public void replaceLineup(String id, Long teamId, List<Long> players) {
        db.update(
            "DELETE FROM save_lineups WHERE save_id=:saveId AND team_id=:teamId",
            new MapSqlParameterSource().addValue("saveId", id).addValue("teamId", teamId)
        );
        var batch = new MapSqlParameterSource[players.size()];
        for (int slot = 0; slot < players.size(); slot++) batch[slot] = new MapSqlParameterSource()
            .addValue("saveId", id)
            .addValue("teamId", teamId)
            .addValue("slot", slot)
            .addValue("playerId", players.get(slot));
        db.batchUpdate(
            "INSERT INTO save_lineups (save_id,team_id,slot_index,player_id) VALUES (:saveId,:teamId,:slot,:playerId)",
            batch
        );
    }
}

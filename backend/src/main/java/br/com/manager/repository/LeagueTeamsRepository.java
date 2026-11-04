package br.com.manager.repository;

import br.com.manager.models.dto.LeagueTeamDto;
import br.com.manager.utils.AssetUrls;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueTeamsRepository {

    private static final RowMapper<LeagueTeamDto> TEAM_ROW_MAPPER = (r, n) ->
        new LeagueTeamDto(
            r.getLong("team_id"),
            r.getLong("league_id"),
            r.getString("league_name"),
            r.getString("country"),
            r.getInt("division"),
            r.getString("name"),
            r.getString("short_name"),
            AssetUrls.crest(r.getLong("team_id")),
            r.getInt("rating"),
            r.getInt("player_count"),
            r.getString("manager_name")
        );

    private static final String SELECT_TEAMS = """
    SELECT t.team_id,t.league_id,t.name,t.short_name,t.rating,
           COALESCE(l.name,'Campeonato da liga') AS league_name,COALESCE(l.country,'Outros') AS country,
           COALESCE(l.division,0) AS division,COALESCE(p.player_count,0) AS player_count,m.name AS manager_name
    FROM save_teams t
    LEFT JOIN catalog_leagues l ON l.id=t.league_id
    LEFT JOIN (SELECT team_id,COUNT(*) AS player_count FROM save_players
               WHERE save_id=:saveId GROUP BY team_id) p ON p.team_id=t.team_id
    LEFT JOIN save_members m ON m.save_id=t.save_id AND m.team_id=t.team_id
    WHERE t.save_id=:saveId
    """;
    private final NamedParameterJdbcTemplate db;

    public LeagueTeamsRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public List<LeagueTeamDto> teams(String saveId) {
        return db.query(
            SELECT_TEAMS + " ORDER BY country,division,league_name,t.name,t.team_id",
            new MapSqlParameterSource("saveId", saveId),
            TEAM_ROW_MAPPER
        );
    }

    public Optional<LeagueTeamDto> find(String saveId, Long teamId) {
        return db
            .query(
                SELECT_TEAMS + " AND t.team_id=:teamId",
                new MapSqlParameterSource("saveId", saveId).addValue("teamId", teamId),
                TEAM_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }
}

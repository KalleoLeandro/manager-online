package br.com.manager.repository;

import br.com.manager.models.dto.WorldCompetitionDtos.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class CompetitionHistoryRepository {

    private final NamedParameterJdbcTemplate db;

    public CompetitionHistoryRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final String UNATTENDED = """
    NOT EXISTS(SELECT 1 FROM save_members m JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id
        WHERE m.save_id=:id AND t.league_id=h.league_id)
    """;
    private static final RowMapper<Competition> COMPETITION = (r, n) ->
        new Competition(
            -r.getLong("id"),
            r.getLong("league_id"),
            r.getInt("season_year"),
            r.getString("label"),
            r.getString("name"),
            r.getString("country"),
            1,
            r.getInt("teams"),
            r.getInt("matches"),
            0,
            r.getObject("first_date", LocalDate.class),
            r.getObject("last_date", LocalDate.class),
            true,
            r.getString("source_url")
        );
    private static final RowMapper<Standing> STANDING = (r, n) ->
        new Standing(
            r.getLong("team_id"),
            r.getString("team_name"),
            r.getInt("played"),
            r.getInt("wins"),
            r.getInt("draws"),
            r.getInt("losses"),
            r.getInt("goals_for"),
            r.getInt("goals_against"),
            r.getInt("points"),
            null
        );

    public void snapshot(String id) {
        var p = new MapSqlParameterSource("id", id);
        db.update(
            """
            INSERT INTO save_historical_seasons(save_id,league_id,season_year,label,name,country,first_date,last_date,source_url)
            SELECT :id,h.league_id,h.season_year,h.label,h.name,h.country,h.first_date,h.last_date,h.source_url
            FROM historical_seasons h JOIN game_saves s ON s.id=:id
            WHERE h.last_date<s.calendar_start_date AND
            """ +
                UNATTENDED +
                """
                AND NOT EXISTS(SELECT 1 FROM save_historical_seasons old
                    WHERE old.save_id=:id AND old.league_id=h.league_id AND old.season_year=h.season_year)
                """,
            p
        );
        db.update(
            """
            INSERT INTO save_historical_standings(season_id,team_id,team_name,position,played,wins,draws,losses,goals_for,goals_against,points)
            SELECT sh.id,t.team_id,t.team_name,t.position,t.played,t.wins,t.draws,t.losses,t.goals_for,t.goals_against,t.points
            FROM save_historical_seasons sh JOIN historical_seasons h ON h.league_id=sh.league_id AND h.season_year=sh.season_year
            JOIN historical_standings t ON t.season_id=h.id WHERE sh.save_id=:id
            AND NOT EXISTS(SELECT 1 FROM save_historical_standings old WHERE old.season_id=sh.id)
            """,
            p
        );
    }

    public List<Competition> competitions(String id) {
        return db.query(
            """
            SELECT h.*,COUNT(t.team_id) AS teams,COALESCE(SUM(t.played),0)/2 AS matches
            FROM save_historical_seasons h JOIN save_historical_standings t ON t.season_id=h.id
            WHERE h.save_id=:id AND
            """ +
                UNATTENDED +
                " GROUP BY h.id ORDER BY h.country,h.season_year DESC",
            new MapSqlParameterSource("id", id),
            COMPETITION
        );
    }

    public List<Standing> standings(String id, long season) {
        return db.query(
            """
            SELECT t.* FROM save_historical_standings t JOIN save_historical_seasons h ON h.id=t.season_id
            WHERE h.save_id=:id AND h.id=:season AND
            """ +
                UNATTENDED +
                " ORDER BY t.position",
            new MapSqlParameterSource("id", id).addValue("season", season),
            STANDING
        );
    }
}

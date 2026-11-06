package br.com.manager.repository;

import br.com.manager.models.dto.WorldCompetitionDtos.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class WorldCompetitionRepository {

    private final NamedParameterJdbcTemplate db;

    public WorldCompetitionRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<Competition> COMPETITION = (r, n) ->
        new Competition(
            r.getLong("id"),
            r.getLong("league_id"),
            r.getInt("season_year"),
            r.getString("label"),
            r.getString("competition_name"),
            r.getString("country"),
            r.getInt("division"),
            r.getInt("team_count"),
            r.getInt("played"),
            r.getInt("scheduled"),
            r.getObject("first_date", LocalDate.class),
            r.getObject("last_date", LocalDate.class),
            false,
            null
        );

    public LocalDate date(String id) {
        return db.queryForObject(
            "SELECT s.calendar_start_date,COALESCE(MIN(COALESCE(t.calendar_day,s.round_number)),s.round_number) AS current_day FROM game_saves s LEFT JOIN save_members m ON m.save_id=s.id LEFT JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id WHERE s.id=:id GROUP BY s.calendar_start_date,s.round_number",
            new MapSqlParameterSource("id", id),
            (r, n) -> r.getObject(1, LocalDate.class).plusDays(r.getInt(2) - 1L)
        );
    }

    public List<Fixture> due(String id, LocalDate date) {
        // Read the barriers once: an inlined CTE can be recomputed for every fixture when
        // PostgreSQL has not analyzed a newly populated league yet.
        var parameters = new MapSqlParameterSource("id", id).addValue("date", date);
        var barriers = new HashMap<Long, LocalDate>();
        db.query(
            """
            SELECT pending.season_id,MIN(pending.match_date) AS first_date
            FROM national_fixtures pending
            WHERE pending.save_id=:id AND pending.status='SCHEDULED'
            AND (pending.home_team_id IN (SELECT team_id FROM save_members WHERE save_id=:id)
                OR pending.away_team_id IN (SELECT team_id FROM save_members WHERE save_id=:id))
            GROUP BY pending.season_id
            """,
            parameters,
            (org.springframework.jdbc.core.RowCallbackHandler) r ->
                barriers.put(r.getLong("season_id"), r.getObject("first_date", LocalDate.class))
        );
        var fixtures = db.query(
            """
            SELECT f.id,f.season_id,f.match_date,f.home_team_id,f.away_team_id,h.rating AS home_rating,a.rating AS away_rating
            FROM national_fixtures f JOIN national_seasons ns ON ns.id=f.season_id
            JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id
            JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id
            WHERE f.save_id=:id AND f.status='SCHEDULED' AND ns.tournament_id IS NULL AND f.match_date<=:date
            AND NOT EXISTS(SELECT 1 FROM save_members m WHERE m.save_id=f.save_id
                AND (m.team_id=f.home_team_id OR m.team_id=f.away_team_id))
            AND NOT EXISTS(SELECT 1 FROM match_sessions ms WHERE ms.fixture_id=f.id)
            ORDER BY f.match_date,f.id
            """,
            parameters,
            (r, n) ->
                new Fixture(
                    r.getLong(1),
                    r.getLong(2),
                    r.getObject(3, LocalDate.class),
                    r.getLong(4),
                    r.getLong(5),
                    r.getInt(6),
                    r.getInt(7)
                )
        );
        return fixtures
            .stream()
            .filter(f -> !barriers.containsKey(f.seasonId()) || f.date().isBefore(barriers.get(f.seasonId())))
            .toList();
    }

    public void finish(String id, List<MapSqlParameterSource> results) {
        if (results.isEmpty()) return;
        db.batchUpdate(
            "UPDATE national_fixtures SET status='PLAYED',home_goals=:home,away_goals=:away WHERE save_id=:id AND id=:fixture AND status='SCHEDULED'",
            results.toArray(MapSqlParameterSource[]::new)
        );
    }

    public List<Competition> competitions(String id) {
        return db.query(
            "SELECT s.*,COALESCE(SUM(CASE WHEN f.status='PLAYED' THEN 1 ELSE 0 END),0) AS played,COALESCE(SUM(CASE WHEN f.status='SCHEDULED' THEN 1 ELSE 0 END),0) AS scheduled FROM national_seasons s LEFT JOIN national_fixtures f ON f.season_id=s.id WHERE s.save_id=:id AND s.tournament_id IS NULL GROUP BY s.id ORDER BY s.country,s.division,s.season_year DESC",
            new MapSqlParameterSource("id", id),
            COMPETITION
        );
    }

    public List<Standing> standings(String id, long season) {
        return db.query(
            "SELECT t.team_id,t.team_name,t.group_name,COUNT(f.id) AS played,COALESCE(SUM(CASE WHEN (f.home_team_id=t.team_id AND f.home_goals>f.away_goals) OR (f.away_team_id=t.team_id AND f.away_goals>f.home_goals) THEN 1 ELSE 0 END),0) AS wins,COALESCE(SUM(CASE WHEN f.home_goals=f.away_goals THEN 1 ELSE 0 END),0) AS draws,COALESCE(SUM(CASE WHEN (f.home_team_id=t.team_id AND f.home_goals<f.away_goals) OR (f.away_team_id=t.team_id AND f.away_goals<f.home_goals) THEN 1 ELSE 0 END),0) AS losses,COALESCE(SUM(CASE WHEN f.home_team_id=t.team_id THEN f.home_goals ELSE f.away_goals END),0) AS gf,COALESCE(SUM(CASE WHEN f.home_team_id=t.team_id THEN f.away_goals ELSE f.home_goals END),0) AS ga FROM national_season_teams t LEFT JOIN national_fixtures f ON f.save_id=t.save_id AND f.season_id=t.season_id AND f.status='PLAYED' AND (f.home_team_id=t.team_id OR f.away_team_id=t.team_id) WHERE t.save_id=:id AND t.season_id=:season GROUP BY t.team_id,t.team_name,t.group_name",
            new MapSqlParameterSource("id", id).addValue("season", season),
            (r, n) ->
                new Standing(
                    r.getLong("team_id"),
                    r.getString("team_name"),
                    r.getInt("played"),
                    r.getInt("wins"),
                    r.getInt("draws"),
                    r.getInt("losses"),
                    r.getInt("gf"),
                    r.getInt("ga"),
                    r.getInt("wins") * 3 + r.getInt("draws"),
                    r.getString("group_name")
                )
        );
    }
}

package br.com.manager.repository;

import br.com.manager.models.dto.TournamentDtos.Entrant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class TournamentParticipationRepository {

    private final NamedParameterJdbcTemplate db;

    public TournamentParticipationRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<Entrant> CLUB = (r, n) ->
        new Entrant(
            r.getLong("team_id"),
            r.getString("name"),
            "",
            r.getInt("national_rank"),
            r.getString("country"),
            null,
            r.getInt("division"),
            r.getInt("rating")
        );

    public List<Entrant> clubs(String id) {
        return db.query(
            "SELECT t.*,l.country,l.division,COALESCE(c.national_rank,999) AS national_rank FROM save_teams t JOIN catalog_leagues l ON l.id=t.league_id LEFT JOIN catalog_teams c ON c.id=t.team_id WHERE t.save_id=:id ORDER BY l.division,t.rating DESC,t.team_id",
            new MapSqlParameterSource("id", id),
            CLUB
        );
    }

    public List<Long> qualified(String id, String target, int year) {
        return db.query(
            "SELECT DISTINCT team_id FROM competition_qualifications WHERE save_id=:id AND target_key=:key AND target_year=:year ORDER BY team_id",
            new MapSqlParameterSource("id", id).addValue("key", target).addValue("year", year),
            (r, n) -> r.getLong(1)
        );
    }

    public Map<Long, Integer> ranking(String id, int year) {
        var rows = db.query(
            """
            SELECT t.team_id,SUM(CASE WHEN (f.home_team_id=t.team_id AND f.home_goals>f.away_goals) OR (f.away_team_id=t.team_id AND f.away_goals>f.home_goals) THEN 3 WHEN f.home_goals=f.away_goals THEN 1 ELSE 0 END) AS points
            FROM national_season_teams t JOIN national_seasons s ON s.id=t.season_id AND s.save_id=t.save_id
            JOIN national_fixtures f ON f.season_id=s.id AND f.status='PLAYED' AND (f.home_team_id=t.team_id OR f.away_team_id=t.team_id)
            WHERE s.save_id=:id AND s.season_year=:year AND s.tournament_id IS NULL AND s.format<>'FRIENDLY' AND s.division=1
            AND NOT EXISTS(SELECT 1 FROM national_fixtures pending WHERE pending.season_id=s.id AND pending.status='SCHEDULED')
            GROUP BY t.team_id
            """,
            new MapSqlParameterSource("id", id).addValue("year", year),
            (r, n) -> Map.entry(r.getLong(1), r.getInt(2))
        );
        var result = new HashMap<Long, Integer>();
        rows.forEach(r -> result.put(r.getKey(), r.getValue()));
        if (result.isEmpty()) db.query(
            "SELECT t.team_id,t.points FROM save_historical_standings t JOIN save_historical_seasons s ON s.id=t.season_id WHERE s.save_id=:id AND s.season_year=(SELECT MAX(x.season_year) FROM save_historical_seasons x WHERE x.save_id=:id)",
            new MapSqlParameterSource("id", id),
            (RowMapper<Map.Entry<Long, Integer>>) (r, n) -> Map.entry(r.getLong(1), r.getInt(2))
        ).forEach(r -> result.put(r.getKey(), r.getValue()));
        return result;
    }

    public List<Long> champions(String id, String key, int first, int last) {
        return db.query(
            "SELECT champion_id FROM tournament_editions WHERE save_id=:id AND competition_key=:key AND season_year BETWEEN :first AND :last AND status='FINISHED' ORDER BY season_year",
            new MapSqlParameterSource("id", id).addValue("key", key).addValue("first", first).addValue("last", last),
            (r, n) -> r.getLong(1)
        );
    }

    public boolean pending(String id, String key, int year) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM tournament_editions WHERE save_id=:id AND competition_key=:key AND season_year=:year AND status='ACTIVE'",
                new MapSqlParameterSource("id", id).addValue("key", key).addValue("year", year),
                Integer.class
            ) > 0
        );
    }

    public record Reference(String key, int year, long team) {}

    public List<Reference> references(String id) {
        return db.query(
            "SELECT r.competition_key,r.season_year,r.team_id FROM continental_title_references r JOIN save_teams t ON t.save_id=:id AND t.team_id=r.team_id JOIN game_saves s ON s.id=:id WHERE r.season_year<EXTRACT(YEAR FROM s.calendar_start_date)",
            new MapSqlParameterSource("id", id),
            (r, n) -> new Reference(r.getString(1), r.getInt(2), r.getLong(3))
        );
    }

    public Map<Long, Integer> internationalRanking(String id, String key, int first, int last) {
        record Result(long home, long away, int homeGoals, int awayGoals) {}
        var p = new MapSqlParameterSource("id", id)
            .addValue("key", key)
            .addValue("first", first - (key.equals("CHAMPIONS") ? 1 : 0))
            .addValue("last", last - (key.equals("CHAMPIONS") ? 1 : 0));
        var result = new HashMap<Long, Integer>();
        db.query(
            "SELECT f.home_team_id,f.away_team_id,f.home_goals,f.away_goals FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN tournament_editions e ON e.id=s.tournament_id WHERE e.save_id=:id AND e.competition_key=:key AND e.season_year BETWEEN :first AND :last AND f.status='PLAYED' AND (:key<>'LIBERTADORES' OR s.tournament_stage>=3)",
            p,
            (r, n) -> new Result(r.getLong(1), r.getLong(2), r.getInt(3), r.getInt(4))
        ).forEach(g -> {
            int win = key.equals("CHAMPIONS") ? 2 : 3;
            result.merge(
                g.home(),
                g.homeGoals() > g.awayGoals() ? win : g.homeGoals() == g.awayGoals() ? 1 : 0,
                Integer::sum
            );
            result.merge(
                g.away(),
                g.awayGoals() > g.homeGoals() ? win : g.homeGoals() == g.awayGoals() ? 1 : 0,
                Integer::sum
            );
        });
        db.query(
            "SELECT t.team_id,s.tournament_stage FROM national_season_teams t JOIN national_seasons s ON s.id=t.season_id JOIN tournament_editions e ON e.id=s.tournament_id WHERE e.save_id=:id AND e.competition_key=:key AND e.season_year BETWEEN :first AND :last AND (:key<>'LIBERTADORES' OR s.tournament_stage>=3)",
            p,
            (r, n) -> Map.entry(r.getLong(1), r.getInt(2))
        ).forEach(t ->
            result.merge(
                t.getKey(),
                key.equals("CHAMPIONS")
                    ? t.getValue() == 0
                        ? 4
                        : t.getValue() == 2
                          ? 5
                          : t.getValue() > 2
                            ? 1
                            : 0
                    : 3,
                Integer::sum
            )
        );
        return result;
    }
}

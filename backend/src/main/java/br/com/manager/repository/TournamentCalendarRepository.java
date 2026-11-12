package br.com.manager.repository;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class TournamentCalendarRepository {

    private final NamedParameterJdbcTemplate db;

    public TournamentCalendarRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public record Booking(long id, long home, long away, LocalDate date, boolean cup, String status) {}

    public List<Booking> bookings(String id, Collection<Long> teams) {
        return db.query(
            "SELECT f.id,f.home_team_id,f.away_team_id,f.match_date,CASE WHEN s.tournament_id IS NOT NULL OR s.format='FRIENDLY' THEN 1 ELSE 0 END,f.status FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=:id AND f.status<>'CANCELLED' AND (f.home_team_id IN(:teams) OR f.away_team_id IN(:teams)) ORDER BY f.match_date,f.id",
            new MapSqlParameterSource("id", id).addValue("teams", teams),
            (r, n) ->
                new Booking(
                    r.getLong(1),
                    r.getLong(2),
                    r.getLong(3),
                    r.getObject(4, LocalDate.class),
                    r.getInt(5) == 1,
                    r.getString(6)
                )
        );
    }

    public Map<Long, LocalDate> humanDates(String id) {
        var result = new HashMap<Long, LocalDate>();
        db.query(
            "SELECT t.team_id,s.calendar_start_date,COALESCE(t.calendar_day,s.round_number) AS current_day FROM save_members m JOIN game_saves s ON s.id=m.save_id JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id WHERE m.save_id=:id",
            new MapSqlParameterSource("id", id),
            (r, n) -> Map.entry(r.getLong(1), r.getObject(2, LocalDate.class).plusDays(r.getInt(3) - 1L))
        ).forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public LocalDate start(String id) {
        return db.queryForObject(
            "SELECT calendar_start_date FROM game_saves WHERE id=:id",
            new MapSqlParameterSource("id", id),
            LocalDate.class
        );
    }

    public void move(String id, long fixture, LocalDate date) {
        db.update(
            "UPDATE national_fixtures SET match_date=:date WHERE save_id=:id AND id=:fixture AND status='SCHEDULED'",
            new MapSqlParameterSource("id", id).addValue("fixture", fixture).addValue("date", date)
        );
    }
}

package br.com.manager.repository;

import br.com.manager.models.dto.CalendarEventDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueCalendarRepository {

    private final NamedParameterJdbcTemplate db;

    public LeagueCalendarRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<CalendarEventDto> EVENT_ROW_MAPPER = (r, n) ->
        new CalendarEventDto(
            "scheduled-" + r.getLong("id"),
            r.getObject("event_date", LocalDate.class),
            r.getString("event_type"),
            r.getString("title"),
            r.getString("description"),
            r.getString("status")
        );

    public LocalDate start(String id) {
        return db.queryForObject(
            "SELECT calendar_start_date FROM game_saves WHERE id=:id",
            new MapSqlParameterSource("id", id),
            (r, n) -> r.getObject("calendar_start_date", LocalDate.class)
        );
    }

    public List<CalendarEventDto> events(String id, Long team, LocalDate start, LocalDate end) {
        return db.query(
            "SELECT id,event_date,event_type,title,description,status FROM league_calendar_events WHERE save_id=:id AND (team_id IS NULL OR team_id=:team) AND event_date BETWEEN :start AND :end ORDER BY event_date,id LIMIT 100",
            new MapSqlParameterSource("id", id).addValue("team", team).addValue("start", start).addValue("end", end),
            EVENT_ROW_MAPPER
        );
    }
}

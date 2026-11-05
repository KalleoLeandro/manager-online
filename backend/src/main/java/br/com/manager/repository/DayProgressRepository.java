package br.com.manager.repository;

import br.com.manager.models.dto.CalendarEventDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class DayProgressRepository {

    private final NamedParameterJdbcTemplate db;

    public DayProgressRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public record Athlete(long id, String name, int fitness, String injury, Integer returnDay) {}

    private static final RowMapper<Athlete> ATHLETE = (r, n) ->
        new Athlete(
            r.getLong("player_id"),
            r.getString("name"),
            r.getInt("fitness"),
            r.getString("injury_name"),
            r.getObject("return_round", Integer.class)
        );

    private MapSqlParameterSource params(String id, long team) {
        return new MapSqlParameterSource("id", id).addValue("team", team);
    }

    public LocalDate date(String id, long team) {
        return db.queryForObject(
            "SELECT s.calendar_start_date,COALESCE(t.calendar_day,s.round_number) AS current_day FROM game_saves s JOIN save_teams t ON t.save_id=s.id WHERE s.id=:id AND t.team_id=:team",
            params(id, team),
            (r, n) -> r.getObject("calendar_start_date", LocalDate.class).plusDays(r.getInt("current_day") - 1L)
        );
    }

    public int day(String id, LocalDate date) {
        return db.queryForObject(
            "SELECT calendar_start_date FROM game_saves WHERE id=:id",
            new MapSqlParameterSource("id", id),
            (r, n) ->
                Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(r.getObject(1, LocalDate.class), date)) + 1
        );
    }

    public LocalDate nextMatch(String id, long team) {
        return db.queryForObject(
            "SELECT MIN(match_date) FROM national_fixtures WHERE save_id=:id AND status='SCHEDULED' AND (home_team_id=:team OR away_team_id=:team)",
            params(id, team),
            (r, n) -> r.getObject(1, LocalDate.class)
        );
    }

    public List<Athlete> athletes(String id, long team) {
        return db.query(
            "SELECT p.player_id,p.name,p.fitness,pr.injury_name,pr.return_round FROM save_players p JOIN save_player_profiles pr ON pr.save_id=p.save_id AND pr.player_id=p.player_id WHERE p.save_id=:id AND p.team_id=:team ORDER BY p.player_id",
            params(id, team),
            ATHLETE
        );
    }

    public void recover(String id, long player) {
        db.update(
            "UPDATE save_player_profiles SET injury_name=NULL,return_round=NULL WHERE save_id=:id AND player_id=:player",
            new MapSqlParameterSource("id", id).addValue("player", player)
        );
    }

    public void injure(String id, long player, int returnDay) {
        var p = new MapSqlParameterSource("id", id).addValue("player", player).addValue("day", returnDay);
        db.update(
            "UPDATE save_player_profiles SET injury_name='Lesão durante o treino',return_round=:day WHERE save_id=:id AND player_id=:player",
            p
        );
        db.update("UPDATE save_players SET fitness=GREATEST(1,fitness-8) WHERE save_id=:id AND player_id=:player", p);
    }

    public void event(String id, long team, LocalDate date, String type, String title, String description) {
        db.update(
            "INSERT INTO league_calendar_events(save_id,team_id,event_date,event_type,title,description,status) VALUES(:id,:team,:date,:type,:title,:description,'COMPLETED')",
            params(id, team)
                .addValue("date", date)
                .addValue("type", type)
                .addValue("title", title)
                .addValue("description", description)
        );
    }

    public boolean completeScheduled(String id, long team, LocalDate date) {
        var p = params(id, team).addValue("date", date);
        int important = db.queryForObject(
            "SELECT COUNT(*) FROM league_calendar_events WHERE save_id=:id AND (team_id=:team OR team_id IS NULL) AND event_date=:date AND status='SCHEDULED' AND event_type IN ('CLUB','TRANSFER')",
            p,
            Integer.class
        );
        // Shared events must not be completed for other managers before their date reaches them.
        db.update(
            "UPDATE league_calendar_events SET status='COMPLETED' WHERE save_id=:id AND team_id=:team AND event_date=:date AND status='SCHEDULED' AND event_type<>'MATCH'",
            p
        );
        return important > 0;
    }

    public int advance(String id, long team, int day) {
        var p = params(id, team).addValue("day", day);
        db.update(
            "INSERT INTO save_team_management(save_id,team_id,formation,mentality,version) SELECT :id,:team,'4-3-3','BALANCED',0 WHERE NOT EXISTS(SELECT 1 FROM save_team_management WHERE save_id=:id AND team_id=:team)",
            p
        );
        // Freeze fallback dates before moving the shared summary; other clubs keep their own date.
        db.update(
            "UPDATE save_teams SET calendar_day=COALESCE(calendar_day,(SELECT round_number FROM game_saves WHERE id=:id)) WHERE save_id=:id",
            p
        );
        db.update("UPDATE save_players SET fitness=LEAST(100,fitness+6) WHERE save_id=:id AND team_id=:team", p);
        db.update("UPDATE save_teams SET calendar_day=:day WHERE save_id=:id AND team_id=:team", p);
        db.update("UPDATE game_saves SET round_number=GREATEST(round_number,:day) WHERE id=:id", p);
        db.update("UPDATE save_team_management SET version=version+1 WHERE save_id=:id AND team_id=:team", p);
        return db.queryForObject(
            "SELECT version FROM save_team_management WHERE save_id=:id AND team_id=:team",
            p,
            Integer.class
        );
    }
}

package br.com.manager.repository;

import br.com.manager.models.dto.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class MatchRepository {

    private final NamedParameterJdbcTemplate db;
    private final JsonMapper json = JsonMapper.builder().build();
    private final RowMapper<MatchSessionDto> sessionMapper = (r, n) ->
        new MatchSessionDto(
            json.readValue(r.getString("state_json"), MatchStateDto.class),
            r.getTimestamp("next_tick_at").toInstant(),
            r.getBoolean("finished")
        );
    private static final RowMapper<MatchFixtureDto> FIXTURE_MAPPER = (r, n) ->
        new MatchFixtureDto(
            r.getLong("id"),
            r.getLong("season_id"),
            r.getObject("match_date", LocalDate.class),
            r.getLong("home_team_id"),
            r.getString("home_name"),
            r.getLong("away_team_id"),
            r.getString("away_name")
        );

    public MatchRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public int speed(String id) {
        return db.queryForObject("SELECT match_speed_percent FROM game_saves WHERE id=:id", params(id), (r, n) ->
            r.getInt("match_speed_percent")
        );
    }

    public void speed(String id, int percent) {
        db.update(
            "UPDATE game_saves SET match_speed_percent=:speed WHERE id=:id",
            params(id).addValue("speed", percent)
        );
    }

    private MapSqlParameterSource params(String id) {
        return new MapSqlParameterSource("id", id);
    }

    public List<String> candidates() {
        return db.query(
            "SELECT id FROM game_saves WHERE status='LIVE' ORDER BY id",
            new MapSqlParameterSource(),
            (r, n) -> r.getString("id")
        );
    }

    public boolean pending(String id) {
        return db.queryForObject("SELECT matchday_pending FROM game_saves WHERE id=:id", params(id), Boolean.class);
    }

    public LocalDate date(String id) {
        return db
            .queryForObject("SELECT calendar_start_date FROM game_saves WHERE id=:id", params(id), (r, n) ->
                r.getObject(1, LocalDate.class)
            )
            .plusDays(
                db.queryForObject("SELECT round_number FROM game_saves WHERE id=:id", params(id), Integer.class) - 1L
            );
    }

    public Optional<LocalDate> nextDate(String id) {
        return db
            .query(
                "SELECT MIN(match_date) AS d FROM national_fixtures WHERE save_id=:id AND status='SCHEDULED'",
                params(id),
                (r, n) -> r.getObject("d", LocalDate.class)
            )
            .stream()
            .filter(Objects::nonNull)
            .findFirst();
    }

    public List<Long> competitions(String id) {
        return db.query(
            "SELECT DISTINCT t.league_id FROM save_members m JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id WHERE m.save_id=:id",
            params(id),
            (r, n) -> r.getLong(1)
        );
    }

    public Optional<MatchTicketDto> nextTicket(String id, long team) {
        return db.query("""
            SELECT f.id,f.match_date,ns.competition_name,f.home_team_id,h.name AS home_name,
                f.away_team_id,a.name AS away_name,
                COALESCE(hp.primary_color,'#334155') AS home_color,COALESCE(hp.secondary_color,'#ffffff') AS home_secondary,
                COALESCE(ap.primary_color,'#334155') AS away_color,COALESCE(ap.secondary_color,'#ffffff') AS away_secondary
            FROM national_fixtures f JOIN national_seasons ns ON ns.id=f.season_id
            JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id
            JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id
            LEFT JOIN catalog_team_palettes hp ON hp.team_id=f.home_team_id
            LEFT JOIN catalog_team_palettes ap ON ap.team_id=f.away_team_id
            WHERE f.save_id=:id AND f.status='SCHEDULED' AND (f.home_team_id=:team OR f.away_team_id=:team)
            ORDER BY f.match_date,f.id LIMIT 1
            """,params(id).addValue("team",team),(r,n)->new MatchTicketDto(r.getLong("id"),r.getObject("match_date",LocalDate.class),
                r.getString("competition_name"),r.getLong("home_team_id"),r.getString("home_name"),r.getString("home_color"),r.getString("home_secondary"),
                r.getLong("away_team_id"),r.getString("away_name"),r.getString("away_color"),r.getString("away_secondary"))).stream().findFirst();
    }

    public Optional<LocalDate> nextHumanDate(String id) {
        return db
            .query(
                "SELECT MIN(f.match_date) AS next_date FROM national_fixtures f WHERE f.save_id=:id AND f.status='SCHEDULED' AND EXISTS(SELECT 1 FROM save_members m WHERE m.save_id=f.save_id AND (m.team_id=f.home_team_id OR m.team_id=f.away_team_id))",
                params(id),
                (r, n) -> r.getObject(1, LocalDate.class)
            )
            .stream()
            .filter(Objects::nonNull)
            .findFirst();
    }

    public String competitionName(String id, long fixture) {
        return db.queryForObject(
            "SELECT s.competition_name FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=:id AND f.id=:fixture",
            params(id).addValue("fixture", fixture),
            String.class
        );
    }

    public List<MatchFixtureDto> humanMatchday(String id, LocalDate date) {
        return db.query(
            "SELECT f.*,h.name AS home_name,a.name AS away_name FROM national_fixtures f JOIN national_seasons ns ON ns.id=f.season_id JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id WHERE f.save_id=:id AND f.match_date=:date AND f.status='SCHEDULED' AND (EXISTS(SELECT 1 FROM save_members m WHERE m.save_id=f.save_id AND (m.team_id=f.home_team_id OR m.team_id=f.away_team_id)) OR ns.tournament_id IS NULL AND EXISTS(SELECT 1 FROM save_members m JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id WHERE m.save_id=f.save_id AND t.league_id=ns.league_id)) ORDER BY f.id",
            params(id).addValue("date", date),
            FIXTURE_MAPPER
        );
    }

    public List<MatchFixtureDto> nextRound(String id, long league) {
        return db.query(
            """
            SELECT f.*,h.name AS home_name,a.name AS away_name FROM national_fixtures f
            JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id
            JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id
            WHERE f.save_id=:id AND f.status='SCHEDULED' AND EXISTS(
              SELECT 1 FROM national_fixtures first_match WHERE first_match.season_id=f.season_id
                AND first_match.round_number=f.round_number AND first_match.id=(
                  SELECT nf.id FROM national_fixtures nf JOIN national_seasons ns ON ns.id=nf.season_id
                  WHERE nf.save_id=:id AND ns.league_id=:league AND nf.status='SCHEDULED'
                  ORDER BY nf.match_date,nf.id LIMIT 1)) ORDER BY f.id
            """,
            params(id).addValue("league", league),
            FIXTURE_MAPPER
        );
    }

    public long nextBatch(String id) {
        db.update("UPDATE game_saves SET matchday_sequence=matchday_sequence+1 WHERE id=:id", params(id));
        return db.queryForObject("SELECT matchday_sequence FROM game_saves WHERE id=:id", params(id), Long.class);
    }

    public int dayNumber(String id, LocalDate date) {
        var start = db.queryForObject("SELECT calendar_start_date FROM game_saves WHERE id=:id", params(id), (r, n) ->
            r.getObject(1, LocalDate.class)
        );
        return Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(start, date)) + 1;
    }

    public void prepareTeam(String id, long team, LocalDate date) {
        int day = dayNumber(id, date);
        int previous = db.queryForObject(
            "SELECT COALESCE(t.calendar_day,s.round_number) FROM save_teams t JOIN game_saves s ON s.id=t.save_id WHERE t.save_id=:id AND t.team_id=:team",
            params(id).addValue("team", team),
            Integer.class
        );
        var p = params(id)
            .addValue("team", team)
            .addValue("day", day)
            .addValue("gain", Math.min(100, Math.max(0, day - previous) * 6));
        db.update("UPDATE save_players SET fitness=LEAST(100,fitness+:gain) WHERE save_id=:id AND team_id=:team", p);
        db.update("UPDATE save_teams SET calendar_day=:day WHERE save_id=:id AND team_id=:team", p);
    }

    public void finishTeam(String id, long team, int day) {
        db.update(
            "UPDATE save_teams SET calendar_day=:day WHERE save_id=:id AND team_id=:team",
            params(id)
                .addValue("team", team)
                .addValue("day", day + 1)
        );
        db.update(
            "UPDATE save_players SET fitness=LEAST(100,fitness+6) WHERE save_id=:id AND team_id=:team",
            params(id).addValue("team", team)
        );
    }

    public void finishIdleTeam(String id, long team, LocalDate date) {
        int day = dayNumber(id, date);
        var p = params(id)
            .addValue("team", team)
            .addValue("day", day + 1);
        db.update(
            "UPDATE save_teams SET calendar_day=GREATEST(COALESCE(calendar_day,1),:day) WHERE save_id=:id AND team_id=:team",
            p
        );
    }

    public void closeBatch(String id) {
        db.update(
            """
            UPDATE game_saves SET status='LOBBY',matchday_pending=FALSE,round_number=GREATEST(round_number,
              (SELECT COALESCE(MAX(t.calendar_day),1) FROM save_teams t JOIN save_members m ON m.save_id=t.save_id AND m.team_id=t.team_id WHERE t.save_id=:id)) WHERE id=:id
            """,
            params(id)
        );
    }

    public List<MatchFixtureDto> fixtures(String id, LocalDate date) {
        return db.query(
            """
            SELECT f.*,h.name AS home_name,a.name AS away_name FROM national_fixtures f
            JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id
            JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id
            WHERE f.save_id=:id AND f.match_date=:date AND f.status='SCHEDULED' ORDER BY f.id
            """,
            params(id).addValue("date", date),
            FIXTURE_MAPPER
        );
    }

    public void recover(String id, int days) {
        if (days <= 0) return;
        db.update(
            "UPDATE save_players SET fitness=LEAST(100,fitness+:gain) WHERE save_id=:id",
            params(id).addValue("gain", Math.min(100, days * 6))
        );
    }

    public void advance(String id, int days, String status) {
        db.update(
            "UPDATE game_saves SET round_number=round_number+:days,status=:status,matchday_pending=FALSE WHERE id=:id",
            params(id).addValue("days", days).addValue("status", status)
        );
    }

    public Set<Long> suspended(String id, long season) {
        return new HashSet<>(
            db.query(
                "SELECT player_id FROM match_player_seasons WHERE save_id=:id AND season_id=:season AND suspension_remaining>0",
                params(id).addValue("season", season),
                (r, n) -> r.getLong(1)
            )
        );
    }

    public void serveSuspensions(String id, long season, long team) {
        db.update(
            """
            UPDATE match_player_seasons SET suspension_remaining=suspension_remaining-1
            WHERE save_id=:id AND season_id=:season AND suspension_remaining>0 AND player_id IN
            (SELECT player_id FROM save_players WHERE save_id=:id AND team_id=:team)
            """,
            params(id).addValue("season", season).addValue("team", team)
        );
    }

    public void create(String id, MatchStateDto state, Instant tick, long batch) {
        db.update(
            "INSERT INTO match_sessions(fixture_id,save_id,state_json,revision,next_tick_at,batch_number) VALUES(:fixture,:id,:state,:revision,:tick,:batch)",
            params(id)
                .addValue("fixture", state.fixtureId)
                .addValue("batch", batch)
                .addValue("state", json.writeValueAsString(state))
                .addValue("revision", state.revision)
                .addValue("tick", Timestamp.from(tick))
        );
    }

    public Optional<MatchSessionDto> session(String id, long fixture) {
        return db
            .query(
                "SELECT * FROM match_sessions WHERE save_id=:id AND fixture_id=:fixture",
                params(id).addValue("fixture", fixture),
                sessionMapper
            )
            .stream()
            .findFirst();
    }

    public List<MatchSessionDto> currentDay(String id) {
        return db.query(
            """
            SELECT m.* FROM match_sessions m
            WHERE m.save_id=:id AND m.batch_number=(SELECT MAX(batch_number) FROM match_sessions WHERE save_id=:id)
            ORDER BY m.fixture_id
            """,
            params(id),
            sessionMapper
        );
    }

    public void save(String id, MatchStateDto s, Instant next) {
        db.update(
            "UPDATE match_sessions SET state_json=:state,revision=:revision,next_tick_at=:tick,finished=:finished WHERE save_id=:id AND fixture_id=:fixture",
            params(id)
                .addValue("state", json.writeValueAsString(s))
                .addValue("revision", s.revision)
                .addValue("tick", Timestamp.from(next))
                .addValue("finished", s.status.equals("FINISHED"))
                .addValue("fixture", s.fixtureId)
        );
    }

    public void resetReady(String id) {
        db.update("UPDATE save_members SET ready=FALSE WHERE save_id=:id", params(id));
    }

    public void finish(String id, MatchStateDto s, int round) {
        br.com.manager.utils.PlayerMatchRatings.refresh(s);
        db.update(
            "UPDATE national_fixtures SET status='PLAYED',home_goals=:h,away_goals=:a WHERE save_id=:id AND id=:fixture AND status='SCHEDULED'",
            params(id).addValue("fixture", s.fixtureId).addValue("h", s.home.goals).addValue("a", s.away.goals)
        );
        if (s.cupMatch) db.update(
            "UPDATE national_fixtures SET home_penalties=:hp,away_penalties=:ap WHERE id=:fixture AND save_id=:id",
            params(id).addValue("fixture", s.fixtureId).addValue("hp", s.homePenalties).addValue("ap", s.awayPenalties)
        );
        int homePoints = s.home.goals > s.away.goals ? 3 : s.home.goals == s.away.goals ? 1 : 0;
        int awayPoints = s.away.goals > s.home.goals ? 3 : s.home.goals == s.away.goals ? 1 : 0;
        for (var team : List.of(s.home, s.away)) {
            int points = team == s.home ? homePoints : awayPoints;
            // Points represent the current national season, not lifetime accumulated points.
            if (!s.cupMatch) db.update(
                """
                UPDATE save_teams SET points=(SELECT COALESCE(SUM(CASE
                  WHEN home_team_id=:team AND home_goals>away_goals OR away_team_id=:team AND away_goals>home_goals THEN 3
                  WHEN home_goals=away_goals THEN 1 ELSE 0 END),0)
                  FROM national_fixtures WHERE save_id=:id AND season_id=:season AND status='PLAYED' AND (home_team_id=:team OR away_team_id=:team))
                WHERE save_id=:id AND team_id=:team
                """,
                params(id).addValue("team", team.id).addValue("season", s.seasonId)
            );
            for (var p : team.players) {
                if (!p.entered) {
                    if(p.talkMoraleDelta!=0||p.talkSharpnessDelta!=0)db.update("UPDATE save_player_profiles SET morale=LEAST(100,GREATEST(1,morale+:morale)),sharpness=LEAST(100,GREATEST(1,sharpness+:sharpness)) WHERE save_id=:id AND player_id=:player",params(id).addValue("player",p.id).addValue("morale",p.talkMoraleDelta).addValue("sharpness",p.talkSharpnessDelta));
                    continue;
                }
                var parameters = params(id)
                    .addValue("season", s.disciplineSeasonId > 0 ? s.disciplineSeasonId : s.seasonId)
                    .addValue("player", p.id);
                db.update(
                    """
                    INSERT INTO match_player_seasons(save_id,season_id,player_id)
                    SELECT :id,:season,:player WHERE NOT EXISTS(SELECT 1 FROM match_player_seasons WHERE save_id=:id AND season_id=:season AND player_id=:player)
                    """,
                    parameters
                );
                parameters
                    .addValue("minutes", p.minutes)
                    .addValue("goals", p.goals)
                    .addValue("assists", p.assists)
                    .addValue("completed", p.passesCompleted)
                    .addValue("missed", p.passesMissed)
                    .addValue("shots", p.shots)
                    .addValue("saves", p.saves)
                    .addValue("yellow", p.yellow)
                    .addValue("red", p.sentOff ? 1 : 0)
                    .addValue("threshold", s.suspensionThreshold)
                    .addValue("rating",p.rating==null?0:p.rating).addValue("rated",p.rating==null?0:1)
                    .addValue("fixture",s.fixtureId).addValue("ratingNullable",p.rating,java.sql.Types.DECIMAL);
                db.update("INSERT INTO match_player_ratings(save_id,fixture_id,player_id,minutes,rating) SELECT :id,:fixture,:player,:minutes,:ratingNullable WHERE NOT EXISTS(SELECT 1 FROM match_player_ratings WHERE save_id=:id AND fixture_id=:fixture AND player_id=:player)",parameters);
                db.update(
                    """
                    UPDATE match_player_seasons SET appearances=appearances+1,minutes=minutes+:minutes,goals=goals+:goals,assists=assists+:assists,
                    passes_completed=passes_completed+:completed,passes_missed=passes_missed+:missed,shots=shots+:shots,saves=saves+:saves,
                    yellow_cards=yellow_cards+:yellow,red_cards=red_cards+:red,rating_total=rating_total+:rating,rated_appearances=rated_appearances+:rated,
                    suspension_remaining=GREATEST(suspension_remaining,CASE WHEN :red=1 OR yellow_count+:yellow>=:threshold THEN 1 ELSE 0 END),
                    yellow_count=CASE WHEN :red=1 OR yellow_count+:yellow>=:threshold THEN 0 ELSE yellow_count+:yellow END
                    WHERE save_id=:id AND season_id=:season AND player_id=:player
                    """,
                    parameters
                );
                db.update(
                    "UPDATE save_players SET fitness=:fitness WHERE save_id=:id AND player_id=:player",
                    parameters.addValue("fitness", p.fitness)
                );
                db.update(
                    "UPDATE save_player_profiles SET morale=LEAST(100,GREATEST(1,morale+:morale)),sharpness=LEAST(100,GREATEST(1,sharpness+:talkSharpness)) WHERE save_id=:id AND player_id=:player",
                    parameters.addValue("morale", (points == 3 ? 3 : points == 0 ? -3 : 0)+p.talkMoraleDelta).addValue("talkSharpness",p.talkSharpnessDelta)
                );
                if (p.injured) db.update(
                    "UPDATE save_player_profiles SET injury_name=:injury,return_round=:returnRound WHERE save_id=:id AND player_id=:player",
                    parameters
                        .addValue("injury", "Lesão durante a partida")
                        .addValue("returnRound", round + p.injuryDays)
                );
            }
        }
    }
}

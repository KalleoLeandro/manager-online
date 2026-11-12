package br.com.manager.repository;

import br.com.manager.models.dto.ClubEventDtos.*;
import br.com.manager.utils.ClubEventRules;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ClubEventRepository {

    private final NamedParameterJdbcTemplate db;

    public ClubEventRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private MapSqlParameterSource p(String id, long team) {
        return new MapSqlParameterSource("id", id).addValue("team", team);
    }

    private static final String SELECT =
        "SELECT e.*,t.name AS team_name,o.name AS opponent_name FROM club_events e JOIN save_teams t ON t.save_id=e.save_id AND t.team_id=e.team_id LEFT JOIN save_teams o ON o.save_id=e.save_id AND o.team_id=e.opponent_id ";
    private static final RowMapper<Booking> ROW = (r, n) ->
        new Booking(
            r.getLong("id"),
            r.getLong("team_id"),
            r.getString("team_name"),
            r.getObject("opponent_id", Long.class),
            r.getString("opponent_name"),
            r.getString("event_type"),
            r.getObject("starts_on", LocalDate.class),
            r.getObject("ends_on", LocalDate.class),
            r.getString("venue_key"),
            r.getLong("cost"),
            r.getString("status"),
            r.getObject("fixture_id", Long.class),
            r.getInt("processed_days"),
            r.getLong("revenue"),
            r.getString("details")
        );

    public List<Booking> list(String id, long team) {
        return db.query(
            SELECT +
                "WHERE e.save_id=:id AND (e.team_id=:team OR e.opponent_id=:team) ORDER BY e.starts_on DESC,e.id DESC",
            p(id, team),
            ROW
        );
    }

    public Booking find(String id, long event) {
        return db
            .query(
                SELECT + "WHERE e.save_id=:id AND e.id=:event",
                new MapSqlParameterSource("id", id).addValue("event", event),
                ROW
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public Booking fixture(String id, long fixture) {
        return db
            .query(
                SELECT + "WHERE e.save_id=:id AND e.fixture_id=:fixture",
                new MapSqlParameterSource("id", id).addValue("fixture", fixture),
                ROW
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public List<Booking> due(String id, long team, LocalDate date) {
        return db.query(
            SELECT +
                "WHERE e.save_id=:id AND (e.team_id=:team OR e.opponent_id=:team) AND e.status IN('PROPOSED','SCHEDULED') AND e.starts_on<=:date ORDER BY e.starts_on,e.id",
            p(id, team).addValue("date", date),
            ROW
        );
    }

    public List<Opponent> opponents(String id, long team) {
        return db.query(
            "SELECT t.team_id,t.name,t.rating,EXISTS(SELECT 1 FROM save_members m WHERE m.save_id=t.save_id AND m.team_id=t.team_id) AS human FROM save_teams t WHERE t.save_id=:id AND t.team_id<>:team ORDER BY t.rating DESC,t.name",
            p(id, team),
            (r, n) ->
                new Opponent(
                    r.getLong(1),
                    r.getString(2),
                    r.getInt(3),
                    r.getBoolean(4),
                    ClubEventRules.friendlyFee(r.getInt(3))
                )
        );
    }

    public int rating(String id, long team) {
        return db.queryForObject(
            "SELECT rating FROM save_teams WHERE save_id=:id AND team_id=:team",
            p(id, team),
            Integer.class
        );
    }

    public LocalDate foundation(String id, long team) {
        return db.queryForObject(
            "SELECT COALESCE(t.foundation_date,c.foundation_date) FROM save_teams t JOIN catalog_teams c ON c.id=t.team_id WHERE t.save_id=:id AND t.team_id=:team",
            p(id, team),
            (r, n) -> r.getObject(1, LocalDate.class)
        );
    }

    public void foundation(String id, long team, LocalDate date) {
        db.update(
            "UPDATE save_teams SET foundation_date=:date WHERE save_id=:id AND team_id=:team AND foundation_date IS NULL",
            p(id, team).addValue("date", date)
        );
    }

    public LocalDate seasonEnd(String id, long team, LocalDate date) {
        return db.queryForObject(
            "SELECT COALESCE(MIN(CASE WHEN s.last_date>=:recent THEN s.last_date END),MAX(s.last_date)) FROM national_seasons s JOIN national_season_teams t ON t.season_id=s.id AND t.save_id=s.save_id WHERE s.save_id=:id AND t.team_id=:team AND s.tournament_id IS NULL AND s.format<>'FRIENDLY'",
            p(id, team).addValue("recent", date.minusDays(30)),
            (r, n) -> r.getObject(1, LocalDate.class)
        );
    }

    public LocalDate lastCompetitiveMatch(String id, long team, LocalDate first, LocalDate last) {
        return db.queryForObject(
            "SELECT MAX(f.match_date) FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=:id AND (f.home_team_id=:team OR f.away_team_id=:team) AND s.format<>'FRIENDLY' AND f.match_date BETWEEN :first AND :last",
            p(id, team).addValue("first", first).addValue("last", last),
            (r, n) -> r.getObject(1, LocalDate.class)
        );
    }

    public boolean fixtureClash(String id, long team, LocalDate first, LocalDate last) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_fixtures WHERE save_id=:id AND (home_team_id=:team OR away_team_id=:team) AND status<>'CANCELLED' AND match_date BETWEEN :first AND :last",
                p(id, team).addValue("first", first).addValue("last", last),
                Integer.class
            ) > 0
        );
    }

    public boolean bookingClash(String id, long team, LocalDate first, LocalDate last, long except) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM club_events WHERE save_id=:id AND (team_id=:team OR opponent_id=:team) AND id<>:except AND status IN('PROPOSED','SCHEDULED') AND starts_on<=:last AND ends_on>=:first",
                p(id, team).addValue("first", first).addValue("last", last).addValue("except", except),
                Integer.class
            ) > 0
        );
    }

    public boolean limited(String id, long team, String type, LocalDate first, LocalDate last) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM club_events WHERE save_id=:id AND team_id=:team AND event_type=:type AND (status<>'CANCELLED' OR processed_days>0) AND starts_on BETWEEN :first AND :last",
                p(id, team).addValue("type", type).addValue("first", first).addValue("last", last),
                Integer.class
            ) > 0
        );
    }

    public long create(
        String id,
        long team,
        Long opponent,
        String type,
        LocalDate first,
        LocalDate last,
        String venue,
        long cost,
        String status
    ) {
        var params = p(id, team)
            .addValue("opponent", opponent)
            .addValue("type", type)
            .addValue("first", first)
            .addValue("last", last)
            .addValue("venue", venue)
            .addValue("cost", cost)
            .addValue("status", status);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(
            "INSERT INTO club_events(save_id,team_id,opponent_id,event_type,starts_on,ends_on,venue_key,cost,status) VALUES(:id,:team,:opponent,:type,:first,:last,:venue,:cost,:status)",
            params,
            key,
            new String[] { "id" }
        );
        return key.getKey().longValue();
    }

    public void calendar(String id, Booking e, long team) {
        for (var date = e.date(); !date.isAfter(e.endDate()); date = date.plusDays(1)) calendarDay(id, e, team, date);
    }

    private void calendarDay(String id, Booking e, long team, LocalDate date) {
        var params = p(id, team)
            .addValue("event", e.id())
            .addValue("date", date)
            .addValue("title", ClubEventRules.kind(e.type()).name())
            .addValue("description", description(e));
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(
            "INSERT INTO league_calendar_events(save_id,team_id,event_date,event_type,title,description,status) VALUES(:id,:team,:date,'CLUB',:title,:description,'SCHEDULED')",
            params,
            key,
            new String[] { "id" }
        );
        db.update(
            "INSERT INTO club_event_calendar(event_id,calendar_id) VALUES(:event,:calendar)",
            params.addValue("calendar", key.getKey().longValue())
        );
    }

    private String description(Booking e) {
        return e.type().equals("FRIENDLY")
            ? e.teamName() +
                  " × " +
                  e.opponentName() +
                  ". Renda dividida em partes iguais." +
                  (e.status().equals("PROPOSED") ? " Aguardando aceite do adversário." : "")
            : e.type().equals("CAMP")
              ? ClubEventRules.venue(e.venue()).name() + " · até " + e.endDate()
              : ClubEventRules.kind(e.type()).description();
    }

    public void update(String id, long event, String status, int days, String details, long revenue) {
        db.update(
            "UPDATE club_events SET status=:status,processed_days=:days,details=:details,revenue=:revenue WHERE save_id=:id AND id=:event",
            new MapSqlParameterSource("id", id)
                .addValue("event", event)
                .addValue("status", status)
                .addValue("days", days)
                .addValue("details", details)
                .addValue("revenue", revenue)
        );
        if (!status.equals("PROPOSED")) db.update(
            "UPDATE league_calendar_events SET status=:status,description=:details WHERE id IN(SELECT calendar_id FROM club_event_calendar WHERE event_id=:event)",
            new MapSqlParameterSource("event", event)
                .addValue(
                    "status",
                    status.equals("COMPLETED") ? "COMPLETED" : status.equals("CANCELLED") ? "CANCELLED" : "SCHEDULED"
                )
                .addValue("details", details)
        );
        if (status.equals("SCHEDULED") && days > 0) db.update(
            "UPDATE league_calendar_events SET status='COMPLETED' WHERE id IN(SELECT calendar_id FROM club_event_calendar WHERE event_id=:event) AND event_date<=:through",
            new MapSqlParameterSource("event", event).addValue(
                "through",
                find(id, event)
                    .date()
                    .plusDays(days - 1L)
            )
        );
    }

    public void attach(String id, long event, long fixture) {
        db.update(
            "UPDATE club_events SET fixture_id=:fixture,status='SCHEDULED' WHERE save_id=:id AND id=:event",
            new MapSqlParameterSource("id", id).addValue("event", event).addValue("fixture", fixture)
        );
        // Confirmed friendlies are represented by the fixture itself in both club calendars.
        db.update(
            "DELETE FROM league_calendar_events WHERE id IN(SELECT calendar_id FROM club_event_calendar WHERE event_id=:event)",
            new MapSqlParameterSource("event", event)
        );
    }

    public long friendly(String id, Booking event) {
        var params = p(id, event.teamId())
            .addValue("opponent", event.opponentId())
            .addValue("league", Long.MIN_VALUE + event.id())
            .addValue("year", event.date().getYear())
            .addValue("label", String.valueOf(event.date().getYear()))
            .addValue("first", event.date())
            .addValue("last", event.date().plusDays(1))
            .addValue("seed", event.id());
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(
            "INSERT INTO national_seasons(save_id,league_id,season_year,label,competition_name,country,division,team_count,rounds,first_date,last_date,draw_seed,format) VALUES(:id,:league,:year,:label,'Amistoso','Amistosos',0,2,1,:first,:last,:seed,'FRIENDLY')",
            params,
            key,
            new String[] { "id" }
        );
        long season = key.getKey().longValue();
        params.addValue("season", season);
        db.update(
            "INSERT INTO national_season_teams(save_id,season_id,team_id,team_name) SELECT :id,:season,team_id,name FROM save_teams WHERE save_id=:id AND team_id IN(:team,:opponent)",
            params
        );
        key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(
            "INSERT INTO national_fixtures(save_id,season_id,round_number,match_date,home_team_id,away_team_id) VALUES(:id,:season,1,:first,:team,:opponent)",
            params,
            key,
            new String[] { "id" }
        );
        long fixture = key.getKey().longValue();
        attach(id, event.id(), fixture);
        return fixture;
    }

    public boolean kickedOff(long fixture) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM match_sessions WHERE fixture_id=:fixture",
                new MapSqlParameterSource("fixture", fixture),
                Integer.class
            ) > 0
        );
    }

    public void cancelFixture(String id, long fixture) {
        db.update(
            "UPDATE national_fixtures SET status='CANCELLED' WHERE save_id=:id AND id=:fixture AND status='SCHEDULED'",
            new MapSqlParameterSource("id", id).addValue("fixture", fixture)
        );
    }

    public int squadTrust(String id, long team) {
        return db.queryForObject(
            "SELECT COALESCE(MAX(squad_trust),60) FROM club_finance_accounts WHERE save_id=:id AND team_id=:team",
            p(id, team),
            Integer.class
        );
    }

    public int fanTrust(String id, long team) {
        return db.queryForObject(
            "SELECT COALESCE(MAX(fan_trust),60) FROM club_finance_accounts WHERE save_id=:id AND team_id=:team",
            p(id, team),
            Integer.class
        );
    }

    public String awards(String id, long team, LocalDate date) {
        var winners = db.query(
            "SELECT p.name,SUM(ms.goals) AS goals,SUM(ms.rating_total) AS ratings,SUM(ms.rated_appearances) AS appearances FROM save_players p JOIN match_player_seasons ms ON ms.save_id=p.save_id AND ms.player_id=p.player_id JOIN national_seasons s ON s.id=ms.season_id WHERE p.save_id=:id AND p.team_id=:team AND s.format<>'FRIENDLY' AND s.last_date BETWEEN :first AND :date GROUP BY p.player_id,p.name HAVING SUM(ms.rated_appearances)>0 ORDER BY SUM(ms.rating_total)/SUM(ms.rated_appearances) DESC,SUM(ms.goals) DESC,p.player_id LIMIT 3",
            p(id, team).addValue("first", date.minusYears(1)).addValue("date", date),
            (r, n) -> r.getString(1)
        );
        return winners.isEmpty()
            ? "O elenco foi homenageado pelo empenho na temporada."
            : "Destaques homenageados: " + String.join(", ", winners) + ".";
    }

    public void effects(String id, long team, int fitness, int morale, int fans, int trust) {
        var roster = roster(id, team);
        var params = p(id, team)
            .addValue("fitness", fitness)
            .addValue("morale", morale)
            .addValue("fans", fans)
            .addValue("trust", trust)
            .addValue("players", roster);
        if (fitness != 0 && !roster.isEmpty()) {
            var healthy = healthy(id, roster);
            if (!healthy.isEmpty()) db.update(
                "UPDATE save_players SET fitness=GREATEST(1,LEAST(100,fitness+:fitness)) WHERE save_id=:id AND team_id=:team AND player_id IN(:healthy)",
                params.addValue("healthy", healthy)
            );
        }
        if (morale != 0 && !roster.isEmpty()) db.update(
            "UPDATE save_player_profiles SET morale=GREATEST(0,LEAST(100,morale+:morale)) WHERE save_id=:id AND player_id IN(:players)",
            params
        );
        if (fans != 0 || trust != 0) db.update(
            "UPDATE club_finance_accounts SET fan_trust=GREATEST(0,LEAST(100,fan_trust+:fans)),squad_trust=GREATEST(0,LEAST(100,squad_trust+:trust)) WHERE save_id=:id AND team_id=:team",
            params
        );
    }

    // Explicit small ID lists avoid correlated UPDATE plans on freshly copied PostgreSQL leagues.
    public List<Long> roster(String id, long team) {
        return db.query("SELECT player_id FROM save_players WHERE save_id=:id AND team_id=:team", p(id, team), (r, n) ->
            r.getLong(1)
        );
    }

    private List<Long> healthy(String id, List<Long> roster) {
        return roster.isEmpty()
            ? List.of()
            : db.query(
                  "SELECT player_id FROM save_player_profiles WHERE save_id=:id AND player_id IN(:players) AND injury_name IS NULL",
                  new MapSqlParameterSource("id", id).addValue("players", roster),
                  (r, n) -> r.getLong(1)
              );
    }

    public void train(String id, long team, String attribute, int experience) {
        if (
            ClubEventRules.VENUES.stream()
                .flatMap(v -> v.focus().stream())
                .noneMatch(attribute::equals)
        ) throw new IllegalArgumentException("Atributo de treino inválido");
        var params = p(id, team).addValue("attribute", attribute).addValue("xp", experience);
        var players = healthy(id, roster(id, team));
        for (long player : players) {
            params.addValue("player", player);
            db.update(
                "INSERT INTO player_training_progress(save_id,player_id,attribute_key,experience) SELECT :id,:player,:attribute,0 WHERE NOT EXISTS(SELECT 1 FROM player_training_progress WHERE save_id=:id AND player_id=:player AND attribute_key=:attribute)",
                params
            );
            int total =
                db.queryForObject(
                    "SELECT experience FROM player_training_progress WHERE save_id=:id AND player_id=:player AND attribute_key=:attribute",
                    params,
                    Integer.class
                ) + experience;
            params.addValue("gain", total / 100).addValue("remainder", total % 100);
            if (total >= 100) db.update(
                "UPDATE save_player_profiles SET " +
                    attribute +
                    "=LEAST(99," +
                    attribute +
                    "+:gain) WHERE save_id=:id AND player_id=:player",
                params
            );
            db.update(
                "UPDATE player_training_progress SET experience=:remainder WHERE save_id=:id AND player_id=:player AND attribute_key=:attribute",
                params
            );
        }
    }

    public void youth(String id, long team) {
        db.update(
            "UPDATE league_juniors SET determination=LEAST(99,determination+1),teamwork=LEAST(99,teamwork+1),potential=LEAST(99,potential+1) WHERE save_id=:id AND team_id=:team",
            p(id, team)
        );
    }

    public int changed(String id, long team) {
        var params = p(id, team);
        db.update("UPDATE save_team_management SET version=version+1 WHERE save_id=:id AND team_id=:team", params);
        return db.queryForObject(
            "SELECT COALESCE(MAX(version),0) FROM save_team_management WHERE save_id=:id AND team_id=:team",
            params,
            Integer.class
        );
    }
}

package br.com.manager.repository;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.PlayerAwardDtos.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class PlayerAwardRepository {

    private final NamedParameterJdbcTemplate db;
    private final JsonMapper json = JsonMapper.builder().build();

    public PlayerAwardRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private MapSqlParameterSource p(String id) {
        return new MapSqlParameterSource("id", id);
    }

    public LocalDate start(String id) {
        return db.queryForObject("SELECT calendar_start_date FROM game_saves WHERE id=:id", p(id), LocalDate.class);
    }

    public boolean covered(long fixture) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM player_performance_coverage WHERE fixture_id=:fixture",
                new MapSqlParameterSource("fixture", fixture),
                Integer.class
            ) > 0
        );
    }

    public void cover(String id, long fixture, boolean estimated) {
        db.update(
            "INSERT INTO player_performance_coverage(save_id,fixture_id,estimated) VALUES(:id,:fixture,:estimated)",
            p(id).addValue("fixture", fixture).addValue("estimated", estimated)
        );
    }

    public List<MatchStateDto> missingSessions(String id, LocalDate through) {
        return db.query(
            "SELECT m.state_json FROM match_sessions m JOIN national_fixtures f ON f.id=m.fixture_id JOIN national_seasons s ON s.id=f.season_id WHERE m.save_id=:id AND m.finished=TRUE AND f.status='PLAYED' AND s.format<>'FRIENDLY' AND f.match_date<=:date AND NOT EXISTS(SELECT 1 FROM player_performance_coverage c WHERE c.fixture_id=f.id) ORDER BY f.id LIMIT 128",
            p(id).addValue("date", through),
            (r, n) -> json.readValue(r.getString(1), MatchStateDto.class)
        );
    }

    private static final RowMapper<Fixture> FIXTURE = (r, n) ->
        new Fixture(
            r.getLong("id"),
            r.getObject("match_date", LocalDate.class),
            r.getLong("home_team_id"),
            r.getLong("away_team_id"),
            r.getString("home_name"),
            r.getString("away_name"),
            r.getInt("home_goals"),
            r.getInt("away_goals")
        );

    public List<Fixture> missingBackground(String id, LocalDate through) {
        return db.query(
            "SELECT f.*,h.name AS home_name,a.name AS away_name FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN game_saves g ON g.id=f.save_id JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id WHERE f.save_id=:id AND f.status='PLAYED' AND s.format<>'FRIENDLY' AND f.match_date>=g.calendar_start_date AND f.match_date<=:date AND NOT EXISTS(SELECT 1 FROM player_performance_coverage c WHERE c.fixture_id=f.id) AND NOT EXISTS(SELECT 1 FROM match_sessions m WHERE m.fixture_id=f.id) ORDER BY f.match_date,f.id LIMIT 192",
            p(id).addValue("date", through),
            FIXTURE
        );
    }

    public List<Fixture> competitionBackfill(String id, Competition competition) {
        String scope = competition.seasonId() != null ? "s.id=:scope" : "s.tournament_id=:scope";
        return db.query(
            "SELECT f.*,h.name AS home_name,a.name AS away_name FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id WHERE f.save_id=:id AND f.status='PLAYED' AND " +
                scope +
                " AND NOT EXISTS(SELECT 1 FROM player_performance_coverage c WHERE c.fixture_id=f.id) AND NOT EXISTS(SELECT 1 FROM match_sessions m WHERE m.fixture_id=f.id) ORDER BY f.id",
            p(id).addValue(
                "scope",
                competition.seasonId() != null ? competition.seasonId() : competition.tournamentId()
            ),
            FIXTURE
        );
    }

    public Map<Long, List<RosterPlayer>> rosters(String id, Collection<Long> teams) {
        var result = new HashMap<Long, List<RosterPlayer>>();
        if (teams.isEmpty()) return result;
        db.query(
            "SELECT player_id,team_id,name,position,ability FROM save_players WHERE save_id=:id AND team_id IN(:teams) ORDER BY ability DESC,player_id",
            p(id).addValue("teams", teams),
            (r, n) -> new RosterPlayer(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getInt(5))
        ).forEach(player -> result.computeIfAbsent(player.team(), k -> new ArrayList<>()).add(player));
        return result;
    }

    public void insert(String id, List<Performance> rows) {
        for (int offset = 0; offset < rows.size(); offset += 200) {
            var batch = rows.subList(offset, Math.min(offset + 200, rows.size()));
            var sql = new StringBuilder(
                "INSERT INTO player_match_performances(save_id,fixture_id,player_id,team_id,player_name,team_name,position,ability,match_date,minutes,goals,assists,passes,tackles,saves,clean_sheets,rating,estimated) VALUES "
            );
            var params = p(id);
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) sql.append(',');
                String k = "p" + i;
                sql.append("(:id,:")
                    .append(k)
                    .append("fixture,:")
                    .append(k)
                    .append("player,:")
                    .append(k)
                    .append("team,:")
                    .append(k)
                    .append("name,:")
                    .append(k)
                    .append("club,:")
                    .append(k)
                    .append("position,:")
                    .append(k)
                    .append("ability,:")
                    .append(k)
                    .append("date,:")
                    .append(k)
                    .append("minutes,:")
                    .append(k)
                    .append("goals,:")
                    .append(k)
                    .append("assists,:")
                    .append(k)
                    .append("passes,:")
                    .append(k)
                    .append("tackles,:")
                    .append(k)
                    .append("saves,:")
                    .append(k)
                    .append("clean,:")
                    .append(k)
                    .append("rating,:")
                    .append(k)
                    .append("estimated)");
                var r = batch.get(i);
                params
                    .addValue(k + "fixture", r.fixtureId())
                    .addValue(k + "player", r.playerId())
                    .addValue(k + "team", r.teamId())
                    .addValue(k + "name", r.playerName())
                    .addValue(k + "club", r.teamName())
                    .addValue(k + "position", r.position())
                    .addValue(k + "ability", r.ability())
                    .addValue(k + "date", r.date())
                    .addValue(k + "minutes", r.minutes())
                    .addValue(k + "goals", r.goals())
                    .addValue(k + "assists", r.assists())
                    .addValue(k + "passes", r.passes())
                    .addValue(k + "tackles", r.tackles())
                    .addValue(k + "saves", r.saves())
                    .addValue(k + "clean", r.cleanSheets())
                    .addValue(k + "rating", r.rating(), java.sql.Types.DECIMAL)
                    .addValue(k + "estimated", r.estimated());
            }
            db.update(sql.toString(), params);
        }
    }

    public boolean missingYear(String id, int year) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN game_saves g ON g.id=f.save_id WHERE f.save_id=:id AND s.format<>'FRIENDLY' AND f.match_date BETWEEN :first AND :last AND f.match_date>=g.calendar_start_date AND f.status<>'CANCELLED' AND (f.status<>'PLAYED' OR NOT EXISTS(SELECT 1 FROM player_performance_coverage c WHERE c.fixture_id=f.id))",
                p(id)
                    .addValue("first", LocalDate.of(year, 1, 1))
                    .addValue("last", LocalDate.of(year, 12, 31)),
                Integer.class
            ) > 0
        );
    }

    private static final RowMapper<Candidate> CANDIDATE = (r, n) ->
        new Candidate(
            r.getLong("player_id"),
            r.getString("player_name"),
            r.getLong("team_id"),
            r.getString("team_name"),
            r.getString("position"),
            r.getInt("ability"),
            r.getInt("appearances"),
            r.getInt("rated"),
            r.getInt("minutes"),
            r.getInt("goals"),
            r.getInt("assists"),
            r.getInt("passes"),
            r.getInt("tackles"),
            r.getInt("saves"),
            r.getInt("clean"),
            r.getDouble("average"),
            r.getInt("estimated")
        );

    public List<Candidate> aggregate(String id, LocalDate first, LocalDate last, Competition competition) {
        String scope =
            competition == null
                ? ""
                : competition.seasonId() != null
                  ? " AND s.id=:scope"
                  : " AND s.tournament_id=:scope";
        var params = p(id).addValue("first", first).addValue("last", last);
        if (competition != null) params.addValue(
            "scope",
            competition.seasonId() != null ? competition.seasonId() : competition.tournamentId()
        );
        return db.query(
            "WITH eligible AS(SELECT p.*,ROW_NUMBER() OVER(PARTITION BY p.player_id ORDER BY p.match_date DESC,p.fixture_id DESC) AS recency FROM player_match_performances p JOIN national_fixtures f ON f.id=p.fixture_id JOIN national_seasons s ON s.id=f.season_id WHERE p.save_id=:id AND p.match_date BETWEEN :first AND :last" +
                scope +
                "), totals AS(SELECT player_id,COUNT(*) AS appearances,COUNT(rating) AS rated,SUM(minutes) AS minutes,SUM(goals) AS goals,SUM(assists) AS assists,SUM(passes) AS passes,SUM(tackles) AS tackles,SUM(saves) AS saves,SUM(clean_sheets) AS clean,COALESCE(AVG(rating),0) AS average,SUM(CASE WHEN estimated THEN 1 ELSE 0 END) AS estimated FROM eligible GROUP BY player_id) SELECT t.*,e.player_name,e.team_id,e.team_name,e.position,e.ability FROM totals t JOIN eligible e ON e.player_id=t.player_id AND e.recency=1",
            params,
            CANDIDATE
        );
    }

    public List<Competition> finished(String id, LocalDate through) {
        var result = new ArrayList<Competition>();
        db.query(
            "SELECT s.id,s.competition_name,s.label,MAX(CASE WHEN f.status='PLAYED' THEN f.match_date END) AS ended,EXISTS(SELECT 1 FROM national_season_teams nt JOIN save_members m ON m.save_id=nt.save_id AND m.team_id=nt.team_id WHERE nt.season_id=s.id) AS relevant FROM national_seasons s JOIN national_fixtures f ON f.season_id=s.id JOIN game_saves g ON g.id=s.save_id WHERE s.save_id=:id AND s.tournament_id IS NULL AND s.format<>'FRIENDLY' AND NOT EXISTS(SELECT 1 FROM player_awards a WHERE a.save_id=:id AND a.scope_key=('S:'||CAST(s.id AS VARCHAR))) GROUP BY s.id,s.competition_name,s.label,g.calendar_start_date HAVING SUM(CASE WHEN f.status='SCHEDULED' THEN 1 ELSE 0 END)=0 AND SUM(CASE WHEN f.status='PLAYED' THEN 1 ELSE 0 END)>0 AND MAX(CASE WHEN f.status='PLAYED' THEN f.match_date END)>=g.calendar_start_date AND MAX(CASE WHEN f.status='PLAYED' THEN f.match_date END)<=:date",
            p(id).addValue("date", through),
            (r, n) ->
                new Competition(
                    "S:" + r.getLong(1),
                    r.getString(2) + " · " + r.getString(3),
                    r.getObject("ended", LocalDate.class).getYear(),
                    r.getObject("ended", LocalDate.class),
                    r.getLong(1),
                    null,
                    r.getBoolean("relevant")
                )
        ).forEach(result::add);
        db.query(
            "SELECT e.id,e.name,e.label,MAX(CASE WHEN f.status='PLAYED' THEN f.match_date END) AS ended,EXISTS(SELECT 1 FROM tournament_teams t JOIN save_members m ON m.save_id=t.save_id AND m.team_id=t.team_id WHERE t.edition_id=e.id) AS relevant FROM tournament_editions e JOIN national_seasons s ON s.tournament_id=e.id JOIN national_fixtures f ON f.season_id=s.id JOIN game_saves g ON g.id=e.save_id WHERE e.save_id=:id AND e.status='FINISHED' AND NOT EXISTS(SELECT 1 FROM player_awards a WHERE a.save_id=:id AND a.scope_key=('T:'||CAST(e.id AS VARCHAR))) GROUP BY e.id,e.name,e.label,g.calendar_start_date HAVING MAX(CASE WHEN f.status='PLAYED' THEN f.match_date END)>=g.calendar_start_date AND MAX(CASE WHEN f.status='PLAYED' THEN f.match_date END)<=:date",
            p(id).addValue("date", through),
            (r, n) ->
                new Competition(
                    "T:" + r.getLong(1),
                    r.getString(2) + " · " + r.getString(3),
                    r.getObject("ended", LocalDate.class).getYear(),
                    r.getObject("ended", LocalDate.class),
                    null,
                    r.getLong(1),
                    r.getBoolean("relevant")
                )
        ).forEach(result::add);
        return result;
    }

    public boolean exists(String id, String key, String scope, int year) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM player_awards WHERE save_id=:id AND award_key=:key AND scope_key=:scope AND award_year=:year",
                p(id).addValue("key", key).addValue("scope", scope).addValue("year", year),
                Integer.class
            ) > 0
        );
    }

    public boolean competitionCovered(String id, Competition competition) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=:id AND f.status='PLAYED' AND " +
                    (competition.seasonId() != null ? "s.id=:scope" : "s.tournament_id=:scope") +
                    " AND NOT EXISTS(SELECT 1 FROM player_performance_coverage c WHERE c.fixture_id=f.id)",
                p(id).addValue(
                    "scope",
                    competition.seasonId() != null ? competition.seasonId() : competition.tournamentId()
                ),
                Integer.class
            ) == 0
        );
    }

    public long award(
        String id,
        String key,
        String scope,
        int year,
        String title,
        String competition,
        LocalDate date,
        int trophy,
        String criteria,
        List<Finalist> finalists,
        boolean automatic
    ) {
        var params = p(id)
            .addValue("key", key)
            .addValue("scope", scope)
            .addValue("year", year)
            .addValue("title", title)
            .addValue("competition", competition)
            .addValue("date", date)
            .addValue("trophy", trophy)
            .addValue("criteria", criteria)
            .addValue("automatic", automatic && !finalists.isEmpty());
        var generated = new GeneratedKeyHolder();
        db.update(
            "INSERT INTO player_awards(save_id,award_key,scope_key,award_year,title,competition_name,awarded_on,trophy_id,criteria,automatic) VALUES(:id,:key,:scope,:year,:title,:competition,:date,:trophy,:criteria,:automatic)",
            params,
            generated,
            new String[] { "id" }
        );
        long award = generated.getKey().longValue();
        for (var finalist : finalists) {
            var c = finalist.player();
            db.update(
                "INSERT INTO player_award_finalists(award_id,place,player_id,player_name,team_id,team_name,position,ability,appearances,rated_appearances,minutes,passes,tackles,goals,assists,saves,clean_sheets,average_rating,score,estimated_matches) VALUES(:award,:place,:player,:name,:team,:club,:position,:ability,:appearances,:rated,:minutes,:passes,:tackles,:goals,:assists,:saves,:clean,:average,:score,:estimated)",
                new MapSqlParameterSource("award", award)
                    .addValue("place", finalist.place())
                    .addValue("player", c.playerId())
                    .addValue("name", c.name())
                    .addValue("team", c.teamId())
                    .addValue("club", c.teamName())
                    .addValue("position", c.position())
                    .addValue("ability", c.ability())
                    .addValue("appearances", c.appearances())
                    .addValue("rated", c.ratedAppearances())
                    .addValue("passes", c.passes())
                    .addValue("tackles", c.tackles())
                    .addValue("minutes", c.minutes())
                    .addValue("goals", c.goals())
                    .addValue("assists", c.assists())
                    .addValue("saves", c.saves())
                    .addValue("clean", c.cleanSheets())
                    .addValue("average", c.averageRating())
                    .addValue("score", finalist.score())
                    .addValue("estimated", c.estimatedMatches())
            );
        }
        return award;
    }

    private static final RowMapper<Finalist> FINALIST = (r, n) ->
        new Finalist(
            r.getInt("place"),
            new Candidate(
                r.getLong("player_id"),
                r.getString("player_name"),
                r.getLong("team_id"),
                r.getString("team_name"),
                r.getString("position"),
                r.getInt("ability"),
                r.getInt("appearances"),
                r.getInt("rated_appearances"),
                r.getInt("minutes"),
                r.getInt("goals"),
                r.getInt("assists"),
                r.getInt("passes"),
                r.getInt("tackles"),
                r.getInt("saves"),
                r.getInt("clean_sheets"),
                r.getDouble("average_rating"),
                r.getInt("estimated_matches")
            ),
            r.getDouble("score")
        );

    private Ceremony ceremony(java.sql.ResultSet r) throws java.sql.SQLException {
        long id = r.getLong("id");
        var finalists = db.query(
            "SELECT * FROM player_award_finalists WHERE award_id=:award ORDER BY place",
            new MapSqlParameterSource("award", id),
            FINALIST
        );
        return new Ceremony(
            id,
            r.getString("award_key"),
            r.getString("scope_key"),
            r.getInt("award_year"),
            r.getString("title"),
            r.getString("competition_name"),
            r.getObject("awarded_on", LocalDate.class),
            r.getInt("trophy_id"),
            r.getString("criteria"),
            finalists
        );
    }

    public List<Ceremony> history(String id) {
        return db.query(
            "SELECT * FROM player_awards WHERE save_id=:id ORDER BY awarded_on DESC,id DESC",
            p(id),
            (r, n) ->
                new Ceremony(
                    r.getLong("id"),
                    r.getString("award_key"),
                    r.getString("scope_key"),
                    r.getInt("award_year"),
                    r.getString("title"),
                    r.getString("competition_name"),
                    r.getObject("awarded_on", LocalDate.class),
                    r.getInt("trophy_id"),
                    r.getString("criteria"),
                    List.of()
                )
        );
    }

    public Ceremony find(String id, long award) {
        return db
            .query(
                "SELECT * FROM player_awards WHERE save_id=:id AND id=:award",
                p(id).addValue("award", award),
                (r, n) -> ceremony(r)
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public List<Ceremony> pending(String id, String member) {
        return db.query(
            "SELECT a.* FROM player_awards a WHERE a.save_id=:id AND a.automatic=TRUE AND NOT EXISTS(SELECT 1 FROM player_award_views v WHERE v.award_id=a.id AND v.member_id=:member) AND (a.scope_key='GLOBAL' OR EXISTS(SELECT 1 FROM national_season_teams t JOIN save_members m ON m.save_id=t.save_id AND m.team_id=t.team_id WHERE t.save_id=:id AND m.id=:member AND ('S:'||CAST(t.season_id AS VARCHAR))=a.scope_key) OR EXISTS(SELECT 1 FROM tournament_teams t JOIN save_members m ON m.save_id=t.save_id AND m.team_id=t.team_id WHERE t.save_id=:id AND m.id=:member AND ('T:'||CAST(t.edition_id AS VARCHAR))=a.scope_key)) ORDER BY a.awarded_on,a.id LIMIT 8",
            p(id).addValue("member", member),
            (r, n) -> ceremony(r)
        );
    }

    public void acknowledge(long award, String member) {
        db.queryForObject(
            "SELECT id FROM player_awards WHERE id=:award FOR UPDATE",
            new MapSqlParameterSource("award", award),
            Long.class
        );
        var params = new MapSqlParameterSource("award", award).addValue("member", member);
        if (
            db.queryForObject(
                "SELECT COUNT(*) FROM player_award_views WHERE award_id=:award AND member_id=:member",
                params,
                Integer.class
            ) == 0
        ) db.update("INSERT INTO player_award_views(award_id,member_id) VALUES(:award,:member)", params);
    }
}

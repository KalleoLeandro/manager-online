package br.com.manager.repository;

import br.com.manager.models.dto.*;
import br.com.manager.utils.NationalLeagueDraw;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class NationalSeasonRepository {

    private final NamedParameterJdbcTemplate db;

    public NationalSeasonRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<NationalSeasonDto> SEASON_MAPPER = (r, n) ->
        new NationalSeasonDto(
            r.getLong("id"),
            r.getInt("season_year"),
            r.getString("label"),
            r.getString("competition_name"),
            r.getString("country"),
            r.getInt("division"),
            r.getInt("team_count"),
            r.getInt("rounds"),
            r.getObject("first_date", LocalDate.class),
            r.getObject("last_date", LocalDate.class),
            r.getString("format")
        );
    private static final RowMapper<NationalFixtureDto> FIXTURE_MAPPER = (r, n) ->
        new NationalFixtureDto(
            r.getLong("id"),
            r.getInt("round_number"),
            r.getObject("match_date", LocalDate.class),
            r.getLong("home_team_id"),
            r.getString("home_name"),
            r.getLong("away_team_id"),
            r.getString("away_name"),
            r.getString("status"),
            r.getObject("home_goals", Integer.class),
            r.getObject("away_goals", Integer.class)
        );

    public NationalCompetitionDto competition(String saveId, Long teamId) {
        return db.queryForObject(
            "SELECT l.id,l.name,l.country,l.division FROM save_teams t JOIN catalog_leagues l ON l.id=t.league_id WHERE t.save_id=:id AND t.team_id=:team",
            new MapSqlParameterSource("id", saveId).addValue("team", teamId),
            (r, n) ->
                new NationalCompetitionDto(
                    r.getLong("id"),
                    r.getString("name"),
                    r.getString("country"),
                    r.getInt("division")
                )
        );
    }

    public List<Long> teams(String saveId, Long leagueId) {
        return db.query(
            "SELECT team_id FROM save_teams WHERE save_id=:id AND league_id=:league ORDER BY team_id",
            new MapSqlParameterSource("id", saveId).addValue("league", leagueId),
            (r, n) -> r.getLong("team_id")
        );
    }

    public List<Long> memberTeams(String saveId) {
        return db.query(
            "SELECT team_id FROM save_members WHERE save_id=:id",
            new MapSqlParameterSource("id", saveId),
            (r, n) -> r.getLong("team_id")
        );
    }

    public boolean hasHuman(String id, long league) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM save_members m JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id WHERE m.save_id=:id AND t.league_id=:league",
                new MapSqlParameterSource("id", id).addValue("league", league),
                Integer.class
            ) > 0
        );
    }

    /** One representative per championship, independent of human participation. */
    public List<Long> competitionTeams(String saveId) {
        return db.query(
            "SELECT MIN(t.team_id) FROM save_teams t JOIN catalog_leagues l ON l.id=t.league_id WHERE t.save_id=:id AND l.selectable=TRUE GROUP BY t.league_id ORDER BY t.league_id",
            new MapSqlParameterSource("id", saveId),
            (r, n) -> r.getLong(1)
        );
    }

    public Map<String, List<Long>> groups(String id, long league, int expectedGroups) {
        var rows = db.query(
            "SELECT t.team_id,c.group_name FROM save_teams t LEFT JOIN catalog_teams c ON c.id=t.team_id WHERE t.save_id=:id AND t.league_id=:league ORDER BY t.team_id",
            new MapSqlParameterSource("id", id).addValue("league", league),
            (r, n) -> Map.entry(r.getLong(1), Objects.toString(r.getString(2), ""))
        );
        var grouped = new LinkedHashMap<String, List<Long>>();
        if (rows.stream().noneMatch(r -> r.getValue().isBlank())) for (var row : rows)
            grouped.computeIfAbsent(row.getValue(), k -> new ArrayList<>()).add(row.getKey());
        if (grouped.size() == expectedGroups) return grouped;
        grouped.clear();
        int groupSize = (rows.size() + expectedGroups - 1) / expectedGroups;
        for (int i = 0; i < rows.size(); i++) grouped
            .computeIfAbsent("Grupo " + (char) ('A' + i / groupSize), k -> new ArrayList<>())
            .add(rows.get(i).getKey());
        return grouped;
    }

    public void assignGroups(String id, long season, Map<String, List<Long>> groups) {
        var batch = new ArrayList<MapSqlParameterSource>();
        groups.forEach((name, teams) ->
            teams.forEach(team ->
                batch.add(
                    new MapSqlParameterSource("id", id)
                        .addValue("season", season)
                        .addValue("team", team)
                        .addValue("group", name)
                )
            )
        );
        db.batchUpdate(
            "UPDATE national_season_teams SET group_name=:group WHERE save_id=:id AND season_id=:season AND team_id=:team",
            batch.toArray(MapSqlParameterSource[]::new)
        );
        db.update(
            "UPDATE national_seasons SET format='DOUBLE_ROUND_ROBIN_GROUPS' WHERE id=:season",
            new MapSqlParameterSource("season", season)
        );
    }

    public LocalDate start(String saveId) {
        return db.queryForObject(
            "SELECT calendar_start_date FROM game_saves WHERE id=:id",
            new MapSqlParameterSource("id", saveId),
            (r, n) -> r.getObject("calendar_start_date", LocalDate.class)
        );
    }

    public int teamDay(String id, long team) {
        return db.queryForObject(
            "SELECT COALESCE(t.calendar_day,s.round_number) FROM save_teams t JOIN game_saves s ON s.id=t.save_id WHERE t.save_id=:id AND t.team_id=:team",
            new MapSqlParameterSource("id", id).addValue("team", team),
            Integer.class
        );
    }

    public boolean exists(String saveId, Long leagueId, int year) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_seasons WHERE save_id=:id AND league_id=:league AND season_year=:year",
                new MapSqlParameterSource("id", saveId).addValue("league", leagueId).addValue("year", year),
                Integer.class
            ) > 0
        );
    }

    public boolean participatesInYear(String saveId, Long teamId, int year) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_seasons s JOIN national_season_teams t ON t.save_id=s.save_id AND t.season_id=s.id WHERE s.save_id=:id AND t.team_id=:team AND s.season_year=:year AND s.tournament_id IS NULL",
                new MapSqlParameterSource("id", saveId).addValue("team", teamId).addValue("year", year),
                Integer.class
            ) > 0
        );
    }

    public Long create(
        String saveId,
        NationalCompetitionDto competition,
        int year,
        String label,
        int teamCount,
        int rounds,
        LocalDate first,
        LocalDate last,
        long seed
    ) {
        var params = new MapSqlParameterSource("id", saveId)
            .addValue("league", competition.leagueId())
            .addValue("year", year)
            .addValue("label", label)
            .addValue("name", competition.name())
            .addValue("country", competition.country())
            .addValue("division", competition.division())
            .addValue("count", teamCount)
            .addValue("rounds", rounds)
            .addValue("first", first)
            .addValue("last", last)
            .addValue("seed", seed);
        db.update(
            "INSERT INTO national_seasons(save_id,league_id,season_year,label,competition_name,country,division,team_count,rounds,first_date,last_date,draw_seed,format) VALUES(:id,:league,:year,:label,:name,:country,:division,:count,:rounds,:first,:last,:seed,'DOUBLE_ROUND_ROBIN')",
            params
        );
        Long season = db.queryForObject(
            "SELECT id FROM national_seasons WHERE save_id=:id AND league_id=:league AND season_year=:year",
            params,
            Long.class
        );
        db.update(
            "INSERT INTO national_season_teams(save_id,season_id,team_id,team_name) SELECT :id,:season,team_id,name FROM save_teams WHERE save_id=:id AND league_id=:league",
            params.addValue("season", season)
        );
        return season;
    }

    public void fixtures(String saveId, Long seasonId, List<NationalLeagueDraw.Fixture> fixtures) {
        var batch = fixtures
            .stream()
            .map(f ->
                new MapSqlParameterSource("id", saveId)
                    .addValue("season", seasonId)
                    .addValue("round", f.round())
                    .addValue("date", f.date())
                    .addValue("home", f.homeTeamId())
                    .addValue("away", f.awayTeamId())
            )
            .toArray(MapSqlParameterSource[]::new);
        db.batchUpdate(
            "INSERT INTO national_fixtures(save_id,season_id,round_number,match_date,home_team_id,away_team_id) VALUES(:id,:season,:round,:date,:home,:away)",
            batch
        );
    }

    public List<NationalSeasonDto> seasons(String saveId, Long teamId) {
        return db.query(
            "SELECT s.* FROM national_seasons s JOIN national_season_teams t ON t.save_id=s.save_id AND t.season_id=s.id WHERE s.save_id=:id AND t.team_id=:team AND s.tournament_id IS NULL ORDER BY s.season_year DESC",
            new MapSqlParameterSource("id", saveId).addValue("team", teamId),
            SEASON_MAPPER
        );
    }

    public List<NationalFixtureDto> fixtures(Long seasonId) {
        return db.query(
            "SELECT f.*,h.team_name AS home_name,a.team_name AS away_name FROM national_fixtures f JOIN national_season_teams h ON h.save_id=f.save_id AND h.season_id=f.season_id AND h.team_id=f.home_team_id JOIN national_season_teams a ON a.save_id=f.save_id AND a.season_id=f.season_id AND a.team_id=f.away_team_id WHERE f.season_id=:season ORDER BY f.round_number,f.id",
            new MapSqlParameterSource("season", seasonId),
            FIXTURE_MAPPER
        );
    }

    public List<CalendarEventDto> week(String saveId, Long teamId, LocalDate first, LocalDate last) {
        return db.query(
            "SELECT f.id,f.home_team_id,f.away_team_id,f.match_date,f.round_number,f.status,s.label,s.competition_name,h.team_name AS home_name,a.team_name AS away_name FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN national_season_teams h ON h.save_id=f.save_id AND h.season_id=f.season_id AND h.team_id=f.home_team_id JOIN national_season_teams a ON a.save_id=f.save_id AND a.season_id=f.season_id AND a.team_id=f.away_team_id WHERE f.save_id=:id AND (f.home_team_id=:team OR f.away_team_id=:team) AND f.match_date BETWEEN :first AND :last ORDER BY f.match_date,f.id",
            new MapSqlParameterSource("id", saveId)
                .addValue("team", teamId)
                .addValue("first", first)
                .addValue("last", last),
            (r, n) ->
                new CalendarEventDto(
                    "national-" + r.getLong("id"),
                    r.getObject("match_date", LocalDate.class),
                    "MATCH",
                    r.getString("home_name") + " × " + r.getString("away_name"),
                    r.getString("competition_name") +
                        " " +
                        r.getString("label") +
                        " · Rodada " +
                        r.getInt("round_number"),
                    r.getString("status").equals("PLAYED") ? "COMPLETED" : r.getString("status"),
                    r.getLong("home_team_id") == teamId ? r.getLong("away_team_id") : r.getLong("home_team_id"),
                    r.getLong("home_team_id") == teamId ? r.getString("away_name") : r.getString("home_name"),
                    r.getLong("home_team_id") == teamId ? "C" : "V"
                )
        );
    }
}

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

    public LocalDate start(String saveId) {
        return db.queryForObject(
            "SELECT calendar_start_date FROM game_saves WHERE id=:id",
            new MapSqlParameterSource("id", saveId),
            (r, n) -> r.getObject("calendar_start_date", LocalDate.class)
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
                "SELECT COUNT(*) FROM national_seasons s JOIN national_season_teams t ON t.save_id=s.save_id AND t.season_id=s.id WHERE s.save_id=:id AND t.team_id=:team AND s.season_year=:year",
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
            "SELECT s.* FROM national_seasons s JOIN national_season_teams t ON t.save_id=s.save_id AND t.season_id=s.id WHERE s.save_id=:id AND t.team_id=:team ORDER BY s.season_year DESC",
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
            "SELECT f.id,f.match_date,f.round_number,f.status,s.label,s.competition_name,h.team_name AS home_name,a.team_name AS away_name FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN national_season_teams h ON h.save_id=f.save_id AND h.season_id=f.season_id AND h.team_id=f.home_team_id JOIN national_season_teams a ON a.save_id=f.save_id AND a.season_id=f.season_id AND a.team_id=f.away_team_id WHERE f.save_id=:id AND (f.home_team_id=:team OR f.away_team_id=:team) AND f.match_date BETWEEN :first AND :last ORDER BY f.match_date,f.id",
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
                    r.getString("status").equals("PLAYED") ? "COMPLETED" : r.getString("status")
                )
        );
    }
}

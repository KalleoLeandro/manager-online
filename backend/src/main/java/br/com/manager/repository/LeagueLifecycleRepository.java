package br.com.manager.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueLifecycleRepository {

    private static final RowMapper<Instant> ACTIVITY_ROW_MAPPER = (r, n) ->
        r.getTimestamp("last_activity_at").toInstant();

    private final NamedParameterJdbcTemplate db;

    public LeagueLifecycleRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public void touch(String id, Instant now) {
        db.update(
            "UPDATE game_saves SET last_activity_at=:now WHERE id=:saveId",
            new MapSqlParameterSource().addValue("saveId", id).addValue("now", Timestamp.from(now))
        );
    }

    public List<String> expiredBefore(Instant cutoff) {
        return db.query(
            "SELECT id FROM game_saves WHERE last_activity_at<:cutoff ORDER BY last_activity_at,id",
            new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)),
            new SingleColumnRowMapper<>(String.class)
        );
    }

    public Optional<Instant> lockActivity(String id) {
        return db
            .query(
                "SELECT last_activity_at FROM game_saves WHERE id=:saveId FOR UPDATE",
                new MapSqlParameterSource("saveId", id),
                ACTIVITY_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }

    public void delete(String id) {
        var params = new MapSqlParameterSource("saveId", id);
        for (var table : List.of(
            "save_roster_policy_leagues",
            "save_roster_policies",
            "save_lineups",
            "save_team_management",
            "save_members",
            "save_players",
            "save_teams"
        ))
            db.update("DELETE FROM " + table + " WHERE save_id=:saveId", params);
        db.update("DELETE FROM game_saves WHERE id=:saveId", params);
    }
}

package br.com.manager.repository;

import java.util.List;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerProfilesRepository {

    private final NamedParameterJdbcTemplate db;

    public PlayerProfilesRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public boolean missingProfiles() {
        return (
            db.queryForObject(
                """
                SELECT (SELECT COUNT(*) FROM catalog_players p WHERE NOT EXISTS(SELECT 1 FROM catalog_player_profiles pr WHERE pr.player_id=p.id))
                     + (SELECT COUNT(*) FROM save_players p WHERE NOT EXISTS(SELECT 1 FROM save_player_profiles pr WHERE pr.save_id=p.save_id AND pr.player_id=p.player_id))
                """,
                new MapSqlParameterSource(),
                new SingleColumnRowMapper<>(Long.class)
            ) > 0
        );
    }

    public List<String> unavailableStarters(String saveId, String memberId) {
        return db.query(
            """
            SELECT p.name FROM save_members m
            JOIN save_lineups l ON l.save_id=m.save_id AND l.team_id=m.team_id
            JOIN save_players p ON p.save_id=l.save_id AND p.player_id=l.player_id
            JOIN game_saves s ON s.id=m.save_id
            LEFT JOIN save_player_profiles pr ON pr.save_id=p.save_id AND pr.player_id=p.player_id
            WHERE m.save_id=:saveId AND m.id=:memberId
              AND (p.fitness<40 OR (pr.injury_name IS NOT NULL AND TRIM(pr.injury_name)<>'' AND (pr.return_round IS NULL OR pr.return_round>s.round_number)))
            """,
            new MapSqlParameterSource("saveId", saveId).addValue("memberId", memberId),
            new SingleColumnRowMapper<>(String.class)
        );
    }
}

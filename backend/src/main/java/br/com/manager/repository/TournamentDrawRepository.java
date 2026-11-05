package br.com.manager.repository;

import br.com.manager.models.dto.TournamentDrawDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class TournamentDrawRepository {

    private final NamedParameterJdbcTemplate db;
    private final JsonMapper json = JsonMapper.builder().build();

    public TournamentDrawRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private final RowMapper<TournamentDrawDto> MAPPER = (r, n) -> {
        var draw = json.readValue(r.getString("payload"), TournamentDrawDto.class);
        return new TournamentDrawDto(
            r.getLong("id"),
            draw.competition(),
            draw.stage(),
            draw.date(),
            draw.startedAt(),
            draw.rows()
        );
    };

    public void record(String id, long edition, int stage, TournamentDrawDto draw) {
        db.update(
            "INSERT INTO tournament_draws(save_id,edition_id,stage_index,draw_date,started_at,payload) SELECT :id,:edition,:stage,:date,:started,:payload WHERE NOT EXISTS(SELECT 1 FROM tournament_draws WHERE edition_id=:edition AND stage_index=:stage)",
            new MapSqlParameterSource("id", id)
                .addValue("edition", edition)
                .addValue("stage", stage)
                .addValue("date", draw.date())
                .addValue("started", draw.startedAt())
                .addValue("payload", json.writeValueAsString(draw))
        );
    }

    public List<TournamentDrawDto> pending(String id, String member) {
        return db.query(
            "SELECT d.* FROM tournament_draws d WHERE d.save_id=:id AND NOT EXISTS(SELECT 1 FROM tournament_draw_views v WHERE v.draw_id=d.id AND v.member_id=:member) ORDER BY d.draw_date,d.id LIMIT 5",
            new MapSqlParameterSource("id", id).addValue("member", member),
            MAPPER
        );
    }

    public boolean acknowledge(String id, String member, long draw) {
        var p = new MapSqlParameterSource("id", id).addValue("member", member).addValue("draw", draw);
        int count = db.queryForObject(
            "SELECT COUNT(*) FROM tournament_draws WHERE id=:draw AND save_id=:id",
            p,
            Integer.class
        );
        if (count == 0) return false;
        db.update(
            "INSERT INTO tournament_draw_views(draw_id,member_id) SELECT :draw,:member WHERE NOT EXISTS(SELECT 1 FROM tournament_draw_views WHERE draw_id=:draw AND member_id=:member)",
            p
        );
        return true;
    }
}

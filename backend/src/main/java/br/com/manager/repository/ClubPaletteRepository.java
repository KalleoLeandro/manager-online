package br.com.manager.repository;

import br.com.manager.models.dto.TeamPaletteDto;
import java.util.*;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClubPaletteRepository {

    private final NamedParameterJdbcTemplate db;

    public ClubPaletteRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public TeamPaletteDto forTeam(Long id) {
        var params = new MapSqlParameterSource("teamId", id);
        var colors = db.query(
            "SELECT color FROM catalog_palette_colors WHERE team_id=:teamId ORDER BY sort_order",
            params,
            new SingleColumnRowMapper<>(String.class)
        );
        var rows = db.query(
            "SELECT * FROM catalog_team_palettes WHERE team_id=:teamId",
            params,
            teamPaletteMapper(colors)
        );
        return rows.isEmpty()
            ? new TeamPaletteDto(
                  "#334155",
                  "#ffffff",
                  false,
                  "#ffffff",
                  null,
                  "#ffffff",
                  List.of("#334155", "#ffffff"),
                  null,
                  "neutral"
              )
            : rows.getFirst();
    }

    private static RowMapper<TeamPaletteDto> teamPaletteMapper(List<String> inputColors) {
        var colors = List.copyOf(inputColors);
        return (r, n) ->
            new TeamPaletteDto(
                r.getString("primary_color"),
                r.getString("secondary_color"),
                r.getBoolean("away_verified"),
                r.getString("away_accent"),
                r.getString("away_source"),
                r.getString("home_accent"),
                colors,
                r.getString("home_source"),
                r.getString("home_method")
            );
    }
}

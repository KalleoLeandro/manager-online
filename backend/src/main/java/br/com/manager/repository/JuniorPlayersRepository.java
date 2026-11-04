package br.com.manager.repository;

import br.com.manager.models.dto.*;
import br.com.manager.models.enums.PlayerAttribute;
import br.com.manager.utils.PlayerCondition;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JuniorPlayersRepository {

    private final NamedParameterJdbcTemplate db;

    public JuniorPlayersRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<ManagedPlayerDto> ROW_MAPPER = (r, n) -> {
        var values = new LinkedHashMap<String, Integer>();
        for (var a : PlayerAttribute.values()) values.put(a.key(), r.getInt(a.key()));
        var technical = new PlayerTechnicalDto(
            Collections.unmodifiableMap(values),
            r.getInt("potential"),
            List.of(),
            null,
            null
        );
        var position = r.getString("position");
        var first = switch (position) {
            case "GOL" -> "Reflexos";
            case "DEF" -> "Desarme";
            case "MEI" -> "Passe";
            default -> "Velocidade";
        };
        var second = switch (position) {
            case "GOL" -> "Posicionamento";
            case "DEF" -> "Marcação";
            case "MEI" -> "Drible";
            default -> "Finalização";
        };
        return new ManagedPlayerDto(
            r.getLong("id"),
            r.getString("name"),
            position,
            switch (r.getString("talent")) {
                case "PROMISING" -> "Promessa";
                case "EXCEPTIONAL" -> "Joia da base";
                default -> "Júnior";
            },
            r.getInt("ability"),
            first,
            values.get(PlayerAttribute.specialty(first).key()),
            second,
            values.get(PlayerAttribute.specialty(second).key()),
            r.getInt("age"),
            r.getString("slot"),
            "/api/players/" + r.getLong("id") + "/portrait",
            100,
            true,
            "YOUTH_GENERATED",
            null,
            technical,
            PlayerCondition.evaluate(r.getInt("ability"), 100, 100, 100, null, null, 1)
        );
    };

    public List<ManagedPlayerDto> players(String saveId, Long teamId) {
        return db.query(
            "SELECT * FROM league_juniors WHERE save_id=:saveId AND team_id=:teamId ORDER BY slot",
            new MapSqlParameterSource("saveId", saveId).addValue("teamId", teamId),
            ROW_MAPPER
        );
    }

    public void create(String saveId, Long teamId, List<JuniorPlayerSeedDto> players) {
        var batch = players
            .stream()
            .map(p -> {
                var params = new MapSqlParameterSource("saveId", saveId)
                    .addValue("teamId", teamId)
                    .addValue("slot", p.slot())
                    .addValue("name", p.name())
                    .addValue("position", p.position())
                    .addValue("ability", p.ability())
                    .addValue("age", p.age())
                    .addValue("potential", p.technical().potential())
                    .addValue("talent", p.talent().name());
                p.technical().attributes().forEach(params::addValue);
                return params;
            })
            .toArray(MapSqlParameterSource[]::new);
        var columns = PlayerAttribute.columns();
        var parameters = Arrays.stream(PlayerAttribute.values())
            .map(a -> ":" + a.key())
            .collect(java.util.stream.Collectors.joining(","));
        db.batchUpdate(
            "INSERT INTO league_juniors(save_id,team_id,slot,name,position,ability,age,potential,talent," +
                columns +
                ") VALUES(:saveId,:teamId,:slot,:name,:position,:ability,:age,:potential,:talent," +
                parameters +
                ")",
            batch
        );
    }
}

package br.com.manager.utils;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.*;
import java.util.*;
import org.springframework.http.HttpStatus;

public final class TacticalPlanRules {

    private TacticalPlanRules() {}

    public static Map<Long, PitchPositionDto> formationPositions(Map<Long, String> players, String formation) {
        return formationPositions(players, formation, false);
    }

    public static Map<Long, PitchPositionDto> formationPositions(
        Map<Long, String> players,
        String formation,
        boolean specialists
    ) {
        var result = new LinkedHashMap<Long, PitchPositionDto>();
        if (specialists && players.size() == 11 && FormationRules.ROLES.containsKey(formation)) {
            var points = FormationRules.points(formation);
            int slot = 0;
            for (var id : players.keySet()) result.put(id, points.get(slot++));
            return result;
        }
        var roles = List.of("GOL", "DEF", "MEI", "ATA");
        if (players.size() != 11 || players.values().stream().filter("GOL"::equals).count() != 1) {
            players.forEach((id, role) -> result.put(id, defaultPosition(id, players)));
            return result;
        }
        var ordered = players
            .entrySet()
            .stream()
            .sorted(Comparator.comparingInt(e -> roles.indexOf(e.getValue())))
            .map(Map.Entry::getKey)
            .toList();
        result.put(ordered.getFirst(), new PitchPositionDto(50.0, 89.0));
        int index = 1,
            row = 0;
        for (String value : formation.split("-")) {
            int count = Integer.parseInt(value);
            for (int i = 0; i < count; i++) result.put(
                ordered.get(index++),
                new PitchPositionDto(
                    (100.0 * (i + 1)) / (count + 1),
                    70.0 - (row * 48.0) / (formation.split("-").length - 1)
                )
            );
            row++;
        }
        return result;
    }

    public static TacticalPlanDto validate(TacticalPlanDto plan, Map<Long, String> starters) {
        if (plan == null) return new TacticalPlanDto();
        for (int value : new int[] {
            plan.passingLength,
            plan.passingRisk,
            plan.tempo,
            plan.width,
            plan.defensiveLine,
            plan.pressing,
        })
            if (value < 0 || value > 100) bad("Estratégias devem estar entre 0 e 100.");
        if (plan.positions == null || plan.positions.size() > 11) bad("Posicionamento inválido.");
        for (var entry : plan.positions.entrySet()) {
            if (entry.getKey() == null || !starters.containsKey(entry.getKey())) bad(
                "Posicione apenas os jogadores em campo do seu time."
            );
            var p = entry.getValue();
            if (p == null || p.x() == null || p.y() == null || !Double.isFinite(p.x()) || !Double.isFinite(p.y())) bad(
                "Coordenadas inválidas."
            );
            boolean keeper = starters.get(entry.getKey()).equals("GOL");
            if (
                p.x() < (keeper ? 35 : 5) ||
                p.x() > (keeper ? 65 : 95) ||
                p.y() < (keeper ? 82 : 8) ||
                p.y() > (keeper ? 96 : 90)
            ) bad(keeper ? "O goleiro deve permanecer na sua área." : "Mantenha os jogadores dentro do campo.");
        }
        return plan;
    }

    public static PitchPositionDto defaultPosition(long id, Map<Long, String> players) {
        String role = players.get(id);
        if ("GOL".equals(role)) return new PitchPositionDto(50.0, 89.0);
        var line = players
            .entrySet()
            .stream()
            .filter(e -> Objects.equals(e.getValue(), role))
            .map(Map.Entry::getKey)
            .toList();
        return new PitchPositionDto(
            (100.0 * (line.indexOf(id) + 1)) / (line.size() + 1),
            "DEF".equals(role) ? 70.0 : "MEI".equals(role) ? 46.0 : 22.0
        );
    }

    private static void bad(String text) {
        throw new DefaultErrorException(HttpStatus.BAD_REQUEST, text);
    }
}

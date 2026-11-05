package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto.Player;
import java.util.*;

/** Specific positions estimated from existing technical data when a source supplies only a broad category. */
public final class PlayerPositionRules {

    private PlayerPositionRules() {}

    public static List<String> preferred(String natural, Map<String, Integer> a) {
        if (!Set.of("GOL", "DEF", "MEI", "ATA").contains(natural)) return List.of(natural);
        if (natural.equals("GOL")) return List.of("GOL");
        int pace = a.getOrDefault("pace", 50),
            cross = a.getOrDefault("crossing", 50),
            tackle = a.getOrDefault("tackling", 50),
            pass = a.getOrDefault("short_passing", 50);
        if (natural.equals("DEF")) return pace > tackle + 4 || cross >= tackle - 5
            ? List.of("LE", "LD", "ALE", "ALD")
            : List.of("ZAG");
        if (natural.equals("MEI")) return tackle >= pass - 2 ? List.of("VOL", "MC") : List.of("MC", "MEI");
        return pace + a.getOrDefault("dribbling", 50) > a.getOrDefault("finishing", 50) * 2 + 4
            ? List.of("PE", "PD", "SA")
            : List.of("CA", "SA");
    }

    public static double fit(String natural, List<String> preferred, String assigned) {
        if (assigned == null) return 1; // Existing persisted matches keep their previous behaviour.
        if (preferred.contains(assigned)) return 1;
        if (natural.equals("GOL") || assigned.equals("GOL")) return .18;
        if (FormationRules.family(assigned).equals(natural)) return .72;
        if (
            (Set.of("ME", "PE", "ALE", "LE").contains(assigned) &&
                preferred.stream().anyMatch(Set.of("ME", "PE", "ALE", "LE")::contains)) ||
            (Set.of("MD", "PD", "ALD", "LD").contains(assigned) &&
                preferred.stream().anyMatch(Set.of("MD", "PD", "ALD", "LD")::contains))
        ) return .75;
        return .42;
    }

    public static double fit(Player p) {
        if (p.assignedPosition == null || p.naturalPosition == null) return 1;
        return fit(p.naturalPosition, p.preferredPositions, p.assignedPosition);
    }

    public static void assign(Player p, String role) {
        if (p.naturalPosition == null) p.naturalPosition = p.position;
        if (p.preferredPositions.isEmpty()) p.preferredPositions = preferred(p.naturalPosition, p.attributes);
        p.assignedPosition = role;
        p.position = FormationRules.family(role);
    }

    public static String roleAt(String original, br.com.manager.models.dto.PitchPositionDto point) {
        if (original.equals("GOL")) return original;
        double x = point.x(),
            y = point.y();
        if (y >= 64) return x < 26 ? "LE" : x > 74 ? "LD" : "ZAG";
        if (y >= 51) return x < 25 ? "ALE" : x > 75 ? "ALD" : "VOL";
        if (y >= 31) return x < 25 ? "ME" : x > 75 ? "MD" : y > 40 ? "MC" : "MEI";
        return x < 28 ? "PE" : x > 72 ? "PD" : "CA";
    }

    public static void applyPlan(br.com.manager.models.dto.MatchStateDto.Team team) {
        var roles = FormationRules.ROLES.get(team.formation);
        var points = FormationRules.points(team.formation);
        for (var player : MatchEngine.active(team))
            if (player.slot >= 0 && player.slot < 11) {
                String role = roles.get(player.slot);
                var point = team.tacticalPlan.positions.get(player.id);
                var base = points.get(player.slot);
                if (point != null && Math.hypot(point.x() - base.x(), point.y() - base.y()) > 8) role = roleAt(
                    role,
                    point
                );
                assign(player, role);
            }
    }
}

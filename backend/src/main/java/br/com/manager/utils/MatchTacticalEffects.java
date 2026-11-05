package br.com.manager.utils;

import br.com.manager.models.dto.*;
import br.com.manager.models.dto.MatchStateDto.*;
import java.util.*;

/** Bounded tactical trade-offs. Neutral settings preserve the calibrated v2 probabilities. */
public final class MatchTacticalEffects {

    private MatchTacticalEffects() {}

    public record Effects(
        double control,
        double creation,
        double attack,
        double defence,
        double pressing,
        double exposure
    ) {}

    private static double signed(int value) {
        return (value - 50) / 50.0;
    }

    public static Effects evaluate(Team team) {
        var plan = team.tacticalPlan == null ? new TacticalPlanDto() : team.tacticalPlan;
        var active = MatchEngine.active(team);
        var roles = new LinkedHashMap<Long, String>();
        active.forEach(p -> roles.put(p.id, p.position));
        boolean specialists = active.stream().anyMatch(p -> p.assignedPosition != null);
        var defaultPositions = TacticalPlanRules.formationPositions(roles, team.formation, specialists);
        if (specialists) {
            var pointsBySlot = FormationRules.points(team.formation);
            for (var p : active) if (p.slot >= 0 && p.slot < 11) defaultPositions.put(p.id, pointsBySlot.get(p.slot));
        }
        var points = new ArrayList<PitchPositionDto>();
        var defensiveX = new ArrayList<Double>();
        var baseDefensiveX = new ArrayList<Double>();
        double sumX = 0,
            baseSumX = 0;
        double attack = 0,
            defence = 0,
            midfield = 0,
            weight = 0,
            adaptation = 0;
        double minX = 100,
            maxX = 0,
            baseMin = 100,
            baseMax = 0;
        for (var player : active) {
            if (player.position.equals("GOL")) continue;
            var defaultPosition = defaultPositions.get(player.id);
            var base = specialists ? defaultPosition : TacticalPlanRules.defaultPosition(player.id, roles);
            var pos =
                plan.positions == null ? defaultPosition : plan.positions.getOrDefault(player.id, defaultPosition);
            double w = player.position.equals("DEF") ? 1.6 : player.position.equals("MEI") ? 1.2 : 1;
            attack += w * (supportAttack(pos.y()) / supportAttack(base.y()) - 1);
            defence += w * (supportDefence(pos.y()) / supportDefence(base.y()) - 1);
            midfield += w * (supportMiddle(pos.y()) / supportMiddle(base.y()) - 1);
            adaptation += Math.max(0, Math.abs(pos.y() - base.y()) - 12) * .003 * w;
            weight += w;
            points.add(pos);
            sumX += pos.x();
            baseSumX += base.x();
            if (player.position.equals("DEF")) {
                defensiveX.add(pos.x());
                baseDefensiveX.add(base.x());
            }
            minX = Math.min(minX, pos.x());
            maxX = Math.max(maxX, pos.x());
            baseMin = Math.min(baseMin, base.x());
            baseMax = Math.max(baseMax, base.x());
        }
        if (weight == 0) return new Effects(1, 1, 1, 1, 1, 0);
        double stretch = maxX - minX - (baseMax - baseMin),
            compactPenalty = Math.min(.12, Math.abs(stretch) / 300);
        double congestion = 0;
        for (int i = 0; i < points.size(); i++) for (int j = i + 1; j < points.size(); j++) {
            var a = points.get(i);
            var b = points.get(j);
            congestion += Math.max(0, 10 - Math.hypot(a.x() - b.x(), a.y() - b.y())) * .004;
        }
        double crowding = Math.min(.18, congestion),
            imbalance = Math.abs(sumX - baseSumX) / Math.max(1, points.size()) / 100;
        double lateralGap = Math.max(0, maxGap(defensiveX) - maxGap(baseDefensiveX)) / 200;
        double pass = signed(plan.passingLength),
            risk = signed(plan.passingRisk),
            tempo = signed(plan.tempo),
            press = signed(plan.pressing),
            line = signed(plan.defensiveLine),
            width = signed(plan.width);
        double fit = 1 - Math.min(.18, adaptation / weight);
        double coordination = active
            .stream()
            .filter(p -> !p.position.equals("GOL"))
            .mapToDouble(p -> MatchEngine.skill(p, "anticipation", "concentration", "teamwork"))
            .average()
            .orElse(50);
        double tired = active
            .stream()
            .mapToInt(p -> p.fitness)
            .average()
            .orElse(100);
        return new Effects(
            clamp(
                (1 +
                    (midfield / weight) * .15 -
                    pass * .025 -
                    risk * .025 -
                    width * .01 -
                    crowding -
                    imbalance * .10 -
                    (plan.counterAttack ? .035 : 0)) *
                    fit,
                .72,
                1.10
            ),
            clamp(
                (1 +
                    (midfield / weight) * .12 +
                    risk * .04 +
                    tempo * .035 +
                    width * .015 -
                    crowding -
                    imbalance * .10) *
                    fit,
                .72,
                1.13
            ),
            clamp(
                (1 + (attack / weight) * .13 + pass * .02 + risk * .025 + width * .02 - crowding * .5) * fit,
                .70,
                1.18
            ),
            clamp(
                (1 +
                    (defence / weight) * .30 -
                    compactPenalty -
                    lateralGap -
                    crowding +
                    press * .018 -
                    line * .015 +
                    (plan.offsideTrap ? clamp((coordination - 70) / 600, -.05, .03) : 0)) *
                    fit,
                .40,
                1.12
            ),
            clamp(1 + press * .14 + line * .025, .80, 1.18),
            Math.max(0, (attack / weight) * .12) +
                Math.max(0, line) * .12 +
                (Math.max(0, press) * Math.max(0, 75 - tired)) / 200 +
                Math.max(0, risk) * .035 +
                Math.max(0, stretch) / 500 +
                lateralGap * .5 +
                imbalance * .20 +
                (plan.offsideTrap ? Math.max(0, 75 - coordination) / 350 : 0)
        );
    }

    private static double maxGap(List<Double> values) {
        if (values.isEmpty()) return 0;
        var sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double previous = 0,
            gap = 0;
        for (double x : sorted) {
            gap = Math.max(gap, x - previous);
            previous = x;
        }
        return Math.max(gap, 100 - previous);
    }

    public static double passAdjustment(Team team, Player carrier) {
        var p = team.tacticalPlan;
        if (p == null) return 0;
        double expertise = carrier.position.equals("GOL")
            ? 0
            : (MatchEngine.skill(carrier, "long_passing", "vision") -
                  MatchEngine.skill(carrier, "short_passing", "first_touch")) /
              300;
        return (
            -signed(p.passingLength) * .035 +
            Math.max(0, signed(p.passingLength)) * expertise -
            signed(p.passingRisk) * .045 -
            signed(p.tempo) * .02
        );
    }

    public static double drainMultiplier(Team team) {
        var p = team.tacticalPlan;
        if (p == null) return 1;
        return 1 + signed(p.pressing) * .30 + signed(p.tempo) * .15;
    }

    public static double counterMultiplier(Team attack, Team defend) {
        if (attack.tacticalPlan == null || !attack.tacticalPlan.counterAttack) return 1;
        return 1 + Math.max(0, signed(defend.tacticalPlan == null ? 50 : defend.tacticalPlan.defensiveLine)) * .12;
    }

    private static double supportAttack(double y) {
        return .25 + .75 * clamp((82 - y) / 60, 0, 1);
    }

    private static double supportDefence(double y) {
        return .15 + .85 * clamp((y - 25) / 45, 0, 1);
    }

    private static double supportMiddle(double y) {
        return Math.max(.2, 1 - Math.abs(y - 46) / 60);
    }

    private static double clamp(double x, double low, double high) {
        return Math.max(low, Math.min(high, x));
    }
}

package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto.Player;
import br.com.manager.models.dto.MatchStateDto.Team;
import java.util.*;

/** Engine v2: role-specific quality, condition and shape. No club identity, table position or human bonus. */
public final class MatchPerformance {

    private MatchPerformance() {}

    public record Sectors(
        double control,
        double creation,
        double attack,
        double defence,
        double pressing,
        double quality,
        double exposure
    ) {}

    public static Sectors sectors(Team team) {
        return sectors(team, true);
    }

    public static Sectors sectors(Team team, boolean applyTactics) {
        var field = MatchEngine.active(team);
        double controlMid = line(field, "MEI", "short_passing", "first_touch", "vision", "teamwork");
        double controlDef = line(field, "DEF", "short_passing", "first_touch", "decisions");
        double controlAttack = line(field, "ATA", "first_touch", "short_passing", "off_ball");
        double creativity = line(field, "MEI", "vision", "long_passing", "dribbling", "technique");
        double forwards = line(field, "ATA", "finishing", "off_ball", "acceleration", "composure");
        double defending = line(field, "DEF", "marking", "tackling", "anticipation", "concentration");
        double midfieldCover = line(field, "MEI", "work_rate", "teamwork", "anticipation", "decisions");
        double pressing = line(field, null, "work_rate", "stamina", "anticipation", "teamwork");
        double quality = field
            .stream()
            .filter(p -> !p.position.equals("GOL"))
            .mapToDouble(p -> MatchEngine.skill(p))
            .average()
            .orElse(12);
        double numbers = Math.min(1, field.size() / 11.0);
        double shape = alignment(field, team.formation);
        var formation = MatchFormationEffects.of(team.formation);
        var tactics = applyTactics
            ? MatchTacticalEffects.evaluate(team)
            : new MatchTacticalEffects.Effects(1, 1, 1, 1, 1, 0);
        return new Sectors(
            (.5 * controlMid + .3 * controlDef + .2 * controlAttack) *
                numbers *
                shape *
                formation.control() *
                tactics.control() *
                TouchlineRules.control(team),
            (.6 * creativity + .25 * forwards + .15 * controlDef) *
                numbers *
                shape *
                formation.control() *
                tactics.creation(),
            (.7 * forwards + .25 * creativity + .05 * controlDef) *
                numbers *
                shape *
                formation.attack() *
                tactics.attack() *
                TouchlineRules.attack(team),
            (.72 * defending + .23 * midfieldCover + .05 * pressing) *
                numbers *
                shape *
                formation.defence() *
                tactics.defence(),
            pressing * numbers * shape * tactics.pressing() * TouchlineRules.pressing(team),
            quality * numbers,
            tactics.exposure()
        );
    }

    private static double line(List<Player> field, String role, String... keys) {
        return field
            .stream()
            .filter(p -> role == null ? !p.position.equals("GOL") : p.position.equals(role))
            .mapToDouble(p -> .75 * MatchEngine.skill(p, keys) + .25 * MatchEngine.skill(p))
            .average()
            .orElse(12);
    }

    private static double alignment(List<Player> field, String formation) {
        var expected = switch (formation) {
            case "4-4-2" -> new int[] { 4, 4, 2 };
            case "3-5-2" -> new int[] { 3, 5, 2 };
            case "4-5-1" -> new int[] { 4, 5, 1 };
            default -> new int[] { 4, 3, 3 };
        };
        int mismatch = 0;
        var roles = List.of("DEF", "MEI", "ATA");
        for (int i = 0; i < roles.size(); i++) {
            String role = roles.get(i);
            mismatch += Math.abs(
                field
                    .stream()
                    .filter(p -> p.position.equals(role))
                    .count() - expected[i]
            );
        }
        return Math.max(.82, 1 - mismatch * .015);
    }

    public static double homePossession(Sectors home, Sectors away) {
        return homePossession(home, away, false);
    }

    public static double homePossession(Sectors home, Sectors away, boolean neutral) {
        return clamp(1 / (1 + Math.exp(-(home.control() - away.control() + (neutral ? 0 : 1.8)) / 45)), .22, .78);
    }

    public static double shotChance(
        Sectors attack,
        Sectors defend,
        Team attacking,
        Team defending,
        boolean home,
        boolean neutral
    ) {
        double chance = shotChance(attack, defend, attacking, defending, home);
        return neutral ? clamp(chance / (home ? 1.08 : .94), .012, .085) : chance;
    }

    public static double shotChance(Sectors attack, Sectors defend, Team attacking, Team defending, boolean home) {
        double duel = (attack.creation() - (.85 * defend.defence() + .15 * defend.pressing())) / 55;
        double quality = (attack.quality() - defend.quality()) / 90;
        double posture = attacking.mentality.equals("ATTACKING")
            ? 1.22
            : attacking.mentality.equals("DEFENSIVE")
              ? .79
              : 1;
        double exposure = defending.mentality.equals("ATTACKING")
            ? 1.16
            : defending.mentality.equals("DEFENSIVE")
              ? .86
              : 1;
        return clamp(
            .050 *
                Math.exp(clamp(duel + quality, -1.25, 1.25)) *
                posture *
                exposure *
                (home ? 1.08 : .94) *
                MatchTacticalEffects.counterMultiplier(attacking, defending) *
                (1 + defend.exposure() * .20),
            .012,
            .085
        );
    }

    public static Player shooter(List<Player> candidates, Random random) {
        double total = 0;
        var weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            var p = candidates.get(i);
            weights[i] =
                (p.position.equals("ATA") ? 5 : p.position.equals("MEI") ? 2 : 1) *
                Math.exp((MatchEngine.skill(p, "finishing", "off_ball", "composure") - 70) / 35);
            total += weights[i];
        }
        double selected = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            selected -= weights[i];
            if (selected <= 0) return candidates.get(i);
        }
        return candidates.getLast();
    }

    public static double expectedGoals(
        double precision,
        double keeper,
        Sectors attack,
        Sectors defend,
        boolean corner,
        boolean freeKick,
        boolean longShot
    ) {
        double base = corner ? .10 : freeKick ? .065 : longShot ? .065 : .15;
        double duel = (precision - keeper) / 110;
        double space = corner || freeKick ? 0 : (attack.attack() - defend.defence()) / 240 + defend.exposure() * .15;
        return clamp(base * Math.exp(clamp(duel + space, -1, 1)), .025, .32);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}

package br.com.manager.utils;

/** Initial game-wide calibration; probabilities are per foul, not per minute or match. */
public final class DisciplineRules {

    private DisciplineRules() {}

    public static double yellowChance(int aggression, int composure, int decisions, boolean alreadyBooked) {
        double aggressive = boundedAttribute(aggression) - 50;
        double lackOfControl = 50 - (boundedAttribute(composure) + boundedAttribute(decisions)) / 2.0;
        double chance = clamp(.20 + aggressive * .0009 + lackOfControl * .0003, .12, .28);
        if (alreadyBooked) {
            // A booked player moderates challenges; poor control and aggression still carry risk.
            chance *= clamp(.15 + aggressive * .0015 + lackOfControl * .0015, .06, .30);
        }
        return chance;
    }

    public static double directRedChance(int aggression, int composure, int decisions) {
        double aggressive = boundedAttribute(aggression) - 50;
        double lackOfControl = 50 - (boundedAttribute(composure) + boundedAttribute(decisions)) / 2.0;
        return .0025 * clamp(1 + aggressive * .006 + lackOfControl * .004, .5, 1.7);
    }

    private static int boundedAttribute(int value) {
        return Math.max(1, Math.min(99, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

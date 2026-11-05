package br.com.manager.utils;

/** Formation policy kept separate so later tactical rules can evolve without changing the event engine. */
public record MatchFormationEffects(double control, double attack, double defence) {
    public static MatchFormationEffects of(String formation) {
        return switch (formation) {
            case "4-4-2" -> new MatchFormationEffects(1.035, .985, 1.015);
            case "3-5-2" -> new MatchFormationEffects(1.065, 1.005, .96);
            case "4-5-1" -> new MatchFormationEffects(1.07, .95, 1.035);
            default -> new MatchFormationEffects(1, 1, 1);
        };
    }
}

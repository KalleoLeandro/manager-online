package br.com.manager.utils;

import br.com.manager.models.dto.PlayerConditionDto;

public final class PlayerCondition {

    private PlayerCondition() {}

    public static PlayerConditionDto evaluate(
        int ability,
        int fitness,
        int morale,
        int sharpness,
        String injury,
        Integer returnRound,
        int round
    ) {
        fitness = Math.clamp(fitness, 0, 100);
        morale = Math.clamp(morale, 0, 100);
        sharpness = Math.clamp(sharpness, 0, 100);
        boolean injured = injury != null && !injury.isBlank() && (returnRound == null || returnRound > round);
        boolean available = !injured && fitness >= 40;
        double factor = available
            ? (0.55 + (0.45 * fitness) / 100.0) *
              (0.85 + (0.15 * sharpness) / 100.0) *
              (morale >= 50 ? 1.0 : 0.9 + morale / 500.0)
            : 0;
        return new PlayerConditionDto(
            fitness,
            available ? Math.max(1, (int) Math.round(ability * factor)) : 0,
            available,
            injured ? "INJURED" : !available ? "UNFIT" : fitness < 75 ? "TIRED" : "AVAILABLE",
            injured ? injury : null,
            injured ? returnRound : null,
            morale,
            sharpness,
            factor
        );
    }
}

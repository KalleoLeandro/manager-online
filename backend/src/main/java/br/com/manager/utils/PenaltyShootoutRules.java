package br.com.manager.utils;

import java.util.random.RandomGenerator;

/** Five alternating kicks with early victory detection, then paired sudden death. */
public final class PenaltyShootoutRules {

    private PenaltyShootoutRules() {}

    public record Score(int home, int away, int homeKicks, int awayKicks) {}

    public static Score play(double homeChance, double awayChance, RandomGenerator random) {
        int home = 0,
            away = 0,
            hk = 0,
            ak = 0;
        for (int pair = 0; pair < 100; pair++) {
            hk++;
            if (random.nextDouble() < Math.clamp(homeChance, .05, .95)) home++;
            if (pair < 5 && (home > away + Math.max(0, 5 - ak) || away > home + Math.max(0, 5 - hk))) return new Score(
                home,
                away,
                hk,
                ak
            );
            ak++;
            if (random.nextDouble() < Math.clamp(awayChance, .05, .95)) away++;
            if (pair < 5 && (home > away + Math.max(0, 5 - ak) || away > home + Math.max(0, 5 - hk))) return new Score(
                home,
                away,
                hk,
                ak
            );
            if (pair >= 4 && home != away) return new Score(home, away, hk, ak);
        }
        throw new IllegalStateException("Disputa de pênaltis não foi concluída.");
    }
}

package br.com.manager.utils;

import java.util.Random;

public final class TrainingRules {

    private TrainingRules() {}

    /** 0.06% per fit athlete/training day; fatigue increases risk, capped at 0.18%. */
    public static double injuryChance(int fitness) {
        return Math.min(.0018, .0006 * (1 + Math.max(0, 80 - fitness) / 20.0));
    }

    public static int injuryDays(Random random) {
        return random.nextDouble() < .9 ? 2 + random.nextInt(5) : 8 + random.nextInt(7);
    }

    public static Random random(String id, long team, long player, java.time.LocalDate date) {
        return new Random(java.util.Objects.hash(id, team, player, date.toEpochDay()) * 0x9E3779B97F4A7C15L);
    }
}

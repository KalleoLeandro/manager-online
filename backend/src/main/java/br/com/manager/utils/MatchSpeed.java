package br.com.manager.utils;

/** Speed changes wall-clock cadence, never the simulation minute or football probabilities. */
public final class MatchSpeed {

    private MatchSpeed() {}

    public static boolean valid(int percent) {
        return percent == 50 || percent == 100 || percent == 200 || percent == 400;
    }

    public static long delay(long millis, int percent) {
        if (!valid(percent)) throw new IllegalArgumentException("Unsupported speed");
        return Math.max(250, Math.round((millis * 100.0) / percent));
    }
}

package br.com.manager.utils;

import java.util.Random;

/** Result-only simulation for unattended competitions; no wall-clock progress or human decisions. */
public final class BackgroundMatchRules {

    private BackgroundMatchRules() {}

    public record Score(int home, int away) {}

    public static Score play(String league, long fixture, int home, int away) {
        return play(league,fixture,home,away,90,false);
    }

    public static Score play(String league,long fixture,int home,int away,int minutes,boolean neutral) {
        var random = new Random(((long) league.hashCode() << 32) ^ fixture ^ 0x574F524C44L);
        double difference = home - away;
        return new Score(
            poisson(Math.clamp((neutral?1.28:1.45) * Math.exp(difference / 18), .15, 5.5)*minutes/90.0, random),
            poisson(Math.clamp((neutral?1.28:1.12) * Math.exp(-difference / 18), .12, 4.8)*minutes/90.0, random)
        );
    }

    private static int poisson(double mean, Random random) {
        double threshold = Math.exp(-mean),
            product = 1;
        int n = 0;
        do {
            n++;
            product *= random.nextDouble();
        } while (product > threshold && n < 16);
        return n - 1;
    }
}

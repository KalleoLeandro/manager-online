package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.PlayerMatchRatings;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlayerRatingDistributionTests {

    @Test
    void simulatedPerformancesProduceDistinctRatingsWithoutRequiringGoals() {
        var ratings = new ArrayList<Double>();
        var nonScorers = new ArrayList<Double>();
        var histogram = new TreeMap<Integer, Integer>();
        for (int seed = 0; seed < 80; seed++) {
            var match = MatchEngineTests.match(12579L + seed * 997L, 70 + (seed % 3) * 5, 75);
            MatchEngineTests.run(match);
            PlayerMatchRatings.refresh(match);
            for (var team : List.of(match.home, match.away))
                for (var p : team.players) {
                    if (p.minutes < 45 || p.rating == null) continue;
                    ratings.add(p.rating);
                    histogram.merge((int) Math.floor(p.rating), 1, Integer::sum);
                    if (p.goals == 0 && p.assists == 0) nonScorers.add(p.rating);
                    assertThat(p.rating).isBetween(1., 10.);
                }
        }
        double average = ratings.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double nearSix =
            ratings
                .stream()
                .filter(r -> r >= 5.8 && r <= 6.3)
                .count() / (double) ratings.size();
        System.out.printf(
            Locale.ROOT,
            "Rating calibration: count=%d mean=%.2f near6=%.1f%% histogram=%s%n",
            ratings.size(),
            average,
            nearSix * 100,
            histogram
        );
        assertThat(average).isBetween(5.7, 7.2);
        assertThat(nearSix).isLessThan(.60);
        assertThat(
            nonScorers
                .stream()
                .filter(r -> r >= 7)
                .count() / (double) nonScorers.size()
        ).isGreaterThan(.10);
        assertThat(
            ratings
                .stream()
                .filter(r -> r < 5.5)
                .count()
        ).isPositive();
    }
}

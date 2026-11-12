package br.com.manager.utils;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.utils.TournamentStandings.Row;
import java.util.*;

/** FIFA group tie-breaks: head-to-head, reapplication, overall goals, conduct, lots. */
public final class FifaGroupStandings {

    private FifaGroupStandings() {}

    public static List<Row> rank(List<Entrant> clubs, List<Game> games, Map<Long, Integer> conduct, long seed) {
        var rows = TournamentStandings.rank(clubs, games, false);
        var result = new ArrayList<Row>();
        var points = new TreeMap<Integer, List<Row>>(Comparator.reverseOrder());
        rows.forEach(r -> points.computeIfAbsent(r.points(), k -> new ArrayList<>()).add(r));
        for (var tied : points.values()) result.addAll(resolve(tied, games, conduct, seed));
        return result;
    }

    private static List<Row> resolve(List<Row> tied, List<Game> games, Map<Long, Integer> conduct, long seed) {
        if (tied.size() < 2) return tied;
        var mini = TournamentStandings.rank(tied.stream().map(Row::team).toList(), games, false);
        mini.sort(
            Comparator.comparingInt(Row::points)
                .reversed()
                .thenComparing(Comparator.comparingInt((Row r) -> r.gf() - r.ga()).reversed())
                .thenComparing(Comparator.comparingInt(Row::gf).reversed())
        );
        var groups = new LinkedHashMap<String, List<Row>>();
        for (var r : mini) {
            String key = r.points() + ":" + (r.gf() - r.ga()) + ":" + r.gf();
            groups
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(
                    tied
                        .stream()
                        .filter(t -> t.team().id() == r.team().id())
                        .findFirst()
                        .orElseThrow()
                );
        }
        if (groups.size() > 1) {
            var result = new ArrayList<Row>();
            for (var group : groups.values()) result.addAll(resolve(group, games, conduct, seed));
            return result;
        }
        return tied
            .stream()
            .sorted(
                Comparator.comparingInt((Row r) -> r.gf() - r.ga())
                    .reversed()
                    .thenComparing(Comparator.comparingInt(Row::gf).reversed())
                    .thenComparingInt(r -> conduct.getOrDefault(r.team().id(), 0))
                    .thenComparingLong(r -> new Random(seed ^ r.team().id()).nextLong())
            )
            .toList();
    }
}

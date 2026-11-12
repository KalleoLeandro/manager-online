package br.com.manager.utils;

import br.com.manager.models.dto.TournamentDtos.Entrant;
import java.util.*;

public final class ClubWorldCupDraw {

    private ClubWorldCupDraw() {}

    public static Map<String, List<Entrant>> draw(List<Entrant> entrants, long seed) {
        if (
            entrants.size() != 32 || entrants.stream().map(Entrant::id).distinct().count() != 32
        ) throw new IllegalArgumentException("O Mundial exige 32 clubes distintos.");
        var remaining = new ArrayList<>(entrants);
        remaining.sort(Comparator.comparingInt(Entrant::rank));
        var pool = new ArrayList<Entrant>();
        for (var allocation : List.of(
            Map.entry("UEFA", 4),
            Map.entry("CONMEBOL", 4),
            Map.entry("UEFA", 8),
            Map.entry("CONMEBOL", 2),
            Map.entry("AFC", 2),
            Map.entry("CAF", 2),
            Map.entry("CONCACAF", 2)
        )) {
            var selected = remaining
                .stream()
                .filter(t -> allocation.getKey().equals(t.region()))
                .limit(allocation.getValue())
                .toList();
            if (selected.size() != allocation.getValue()) throw new IllegalArgumentException(
                "Distribuição de vagas do Mundial incompleta."
            );
            pool.addAll(selected);
            remaining.removeAll(selected);
        }
        pool.addAll(remaining);
        var groups = new ArrayList<List<Entrant>>();
        for (int i = 0; i < 8; i++) groups.add(new ArrayList<>());
        if (!place(0, 0, pool, groups, new Random(seed))) throw new IllegalStateException(
            "Não foi possível respeitar as associações e confederações do sorteio do Mundial."
        );
        var result = new LinkedHashMap<String, List<Entrant>>();
        for (int i = 0; i < 8; i++) result.put("Grupo " + (char) ('A' + i), List.copyOf(groups.get(i)));
        return result;
    }

    private static boolean place(int pot, int index, List<Entrant> pool, List<List<Entrant>> groups, Random random) {
        if (pot == 4) return groups.stream().allMatch(g -> g.stream().anyMatch(t -> "UEFA".equals(t.region())));
        if (index == 8) return place(pot + 1, 0, pool, groups, random);
        var team = pool.get(pot * 8 + index);
        var order = new ArrayList<>(java.util.stream.IntStream.range(0, 8).boxed().toList());
        Collections.shuffle(order, random);
        for (int i : order) {
            var g = groups.get(i);
            if (
                g.size() != pot || g.stream().anyMatch(t -> Objects.equals(t.association(), team.association()))
            ) continue;
            long same = g
                .stream()
                .filter(t -> Objects.equals(t.region(), team.region()))
                .count();
            if (same >= ("UEFA".equals(team.region()) ? 2 : 1)) continue;
            g.add(team);
            if (place(pot, index + 1, pool, groups, random)) return true;
            g.removeLast();
        }
        return false;
    }
}

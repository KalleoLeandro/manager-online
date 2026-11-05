package br.com.manager.utils;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.utils.TournamentRulebooks.Stage;
import java.util.*;

public final class KnockoutDraw {

    private KnockoutDraw() {}

    public static List<Tie> draw(long edition, int stageIndex, Stage rules, List<Entrant> entrants, long seed) {
        if (
            entrants.size() != rules.entrants() ||
            entrants.size() % 2 != 0 ||
            entrants.stream().map(Entrant::id).distinct().count() != entrants.size()
        ) throw new IllegalArgumentException("Número de participantes incompatível com o regulamento da fase.");
        var random = new Random(seed);
        var pool = new ArrayList<>(entrants);
        var pairs = new ArrayList<List<Entrant>>();
        String policy = rules.drawRule();
        if (policy.equals("RNC_COMPLEMENTARY_PAIRS") || policy.equals("SPORTING_RANK_BRACKET")) {
            pool.sort(Comparator.comparingInt(Entrant::rank).thenComparingLong(Entrant::id));
            while (!pool.isEmpty()) pairs.add(List.of(pool.removeFirst(), pool.removeLast()));
        } else if (policy.equals("RNC_TWO_POTS")) {
            pool.sort(Comparator.comparingInt(Entrant::rank).thenComparingLong(Entrant::id));
            var first = new ArrayList<>(pool.subList(0, pool.size() / 2));
            var second = new ArrayList<>(pool.subList(pool.size() / 2, pool.size()));
            Collections.shuffle(first, random);
            Collections.shuffle(second, random);
            for (int i = 0; i < first.size(); i++) pairs.add(List.of(first.get(i), second.get(i)));
        } else if (policy.startsWith("PRO_AMATEUR")) {
            var amateurs = new ArrayList<>(
                pool
                    .stream()
                    .filter(t -> t.division() >= 3)
                    .toList()
            );
            var professionals = new ArrayList<>(
                pool
                    .stream()
                    .filter(t -> t.division() < 3)
                    .toList()
            );
            Collections.shuffle(amateurs, random);
            Collections.shuffle(professionals, random);
            while (!amateurs.isEmpty() && !professionals.isEmpty())
                pairs.add(List.of(amateurs.removeFirst(), professionals.removeFirst()));
            var rest = new ArrayList<>(amateurs);
            rest.addAll(professionals);
            Collections.shuffle(rest, random);
            while (!rest.isEmpty()) pairs.add(List.of(rest.removeFirst(), rest.removeFirst()));
        } else {
            if (!policy.equals("PREVIOUS_BRACKET")) Collections.shuffle(pool, random);
            while (!pool.isEmpty()) {
                var a = pool.removeFirst();
                int index = 0;
                if (policy.contains("GEOGRAPHIC") && a.region() != null) for (int i = 0; i < pool.size(); i++) if (
                    a.region().equals(pool.get(i).region())
                ) {
                    index = i;
                    break;
                }
                pairs.add(List.of(a, pool.remove(index)));
            }
        }
        var result = new ArrayList<Tie>();
        for (var pair : pairs) {
            var home = pair.get(0);
            var away = pair.get(1);
            boolean switchHome =
                policy.contains("LOWER_HOSTS") || policy.contains("AMATEUR_HOSTS")
                    ? home.division() < away.division() || (home.division() == away.division() && random.nextBoolean())
                    : !policy.equals("SPORTING_RANK_BRACKET") && random.nextBoolean();
            if (switchHome) {
                var swap = home;
                home = away;
                away = swap;
            }
            result.add(new Tie(stageIndex, result.size() + 1, home.id(), away.id(), null));
        }
        return result;
    }
}

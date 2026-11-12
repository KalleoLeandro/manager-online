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
        var professionalPot = new HashSet<Long>();
        String policy = rules.drawRule();
        if (policy.equals("FIFA_GROUP_BRACKET")) {
            for (int first : new int[] { 1, 2 }) {
                for (String[] match : new String[][] { { "A", "B" }, { "C", "D" }, { "E", "F" }, { "G", "H" } }) {
                    var a = pool
                        .stream()
                        .filter(t -> ("Grupo " + match[0]).equals(t.region()) && t.rank() == first)
                        .findFirst()
                        .orElseThrow();
                    var b = pool
                        .stream()
                        .filter(t -> ("Grupo " + match[1]).equals(t.region()) && t.rank() == 3 - first)
                        .findFirst()
                        .orElseThrow();
                    pairs.add(List.of(a, b));
                }
            }
        } else if (policy.equals("GROUP_WINNERS_V_RUNNERS")) {
            var winners = new ArrayList<>(
                pool
                    .stream()
                    .filter(t -> t.rank() == 1)
                    .toList()
            );
            var runners = new ArrayList<>(
                pool
                    .stream()
                    .filter(t -> t.rank() == 2)
                    .toList()
            );
            boolean matched = false;
            for (int attempt = 0; attempt < 1000 && !matched; attempt++) {
                Collections.shuffle(runners, random);
                matched = true;
                for (int i = 0; i < winners.size(); i++) if (
                    Objects.equals(winners.get(i).region(), runners.get(i).region())
                ) matched = false;
            }
            if (!matched) throw new IllegalStateException("Não foi possível separar clubes do mesmo grupo.");
            for (int i = 0; i < winners.size(); i++) pairs.add(List.of(runners.get(i), winners.get(i)));
        } else if (policy.equals("UEFA_PLAYOFF") || policy.equals("UEFA_R16")) {
            pool.sort(Comparator.comparingInt(Entrant::rank));
            var top = new ArrayList<>(pool.subList(0, 8));
            var bottom = new ArrayList<>(pool.subList(8, 16));
            for (int block = 0; block < 4; block++) {
                var high = new ArrayList<>(top.subList(block * 2, block * 2 + 2));
                var low = new ArrayList<>(bottom.subList(6 - block * 2, 8 - block * 2));
                Collections.shuffle(high, random);
                Collections.shuffle(low, random);
                for (int i = 0; i < 2; i++) pairs.add(List.of(low.get(i), high.get(i)));
            }
            if (policy.equals("UEFA_R16")) {
                var seeded = new HashMap<Integer, List<Entrant>>();
                pairs.forEach(pair -> seeded.put(pair.get(1).rank(), pair));
                pairs.clear();
                for (int seedRank : new int[] { 1, 8, 4, 5, 2, 7, 3, 6 }) pairs.add(seeded.get(seedRank));
            }
        } else if (policy.equals("RNC_COMPLEMENTARY_PAIRS") || policy.equals("SPORTING_RANK_BRACKET")) {
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
            var professionals = new ArrayList<>(
                pool
                    .stream()
                    .filter(t -> t.division() < 3)
                    .sorted(Comparator.comparingInt(Entrant::division).thenComparingInt(Entrant::rank))
                    .limit(rules.entrants() == 64 ? 32 : rules.entrants())
                    .toList()
            );
            professionals.forEach(t -> professionalPot.add(t.id()));
            var amateurs = new ArrayList<>(
                pool
                    .stream()
                    .filter(t -> !professionalPot.contains(t.id()))
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
        } else if (policy.contains("LOWER_HOSTS")) {
            Collections.shuffle(pool, random);
            if (
                policy.startsWith("REGIONAL_WINNERS") ||
                policy.startsWith("SUPERCUP_ENTRANTS") ||
                policy.startsWith("OVERSEAS_ENTRANTS")
            ) {
                var protectedClubs = new ArrayList<>(
                    pool
                        .stream()
                        .filter(t ->
                            policy.startsWith("REGIONAL") ? t.stage().equals("PRELIM") : t.stage().equals(rules.key())
                        )
                        .toList()
                );
                pool.removeAll(protectedClubs);
                for (var club : protectedClubs) {
                    pool.sort(Comparator.comparingInt(Entrant::division).reversed());
                    var opponent = policy.startsWith("REGIONAL")
                        ? pool
                              .stream()
                              .filter(t -> t.division() == 1)
                              .findFirst()
                              .orElse(pool.getFirst())
                        : pool.getFirst();
                    pool.remove(opponent);
                    pairs.add(List.of(club, opponent));
                }
            }
            pool.sort(Comparator.comparingInt(Entrant::division));
            while (!pool.isEmpty()) pairs.add(List.of(pool.removeFirst(), pool.removeLast()));
        } else {
            if (!List.of("PREVIOUS_BRACKET", "UEFA_FIXED_BRACKET").contains(policy)) Collections.shuffle(pool, random);
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
                    : !List.of(
                          "SPORTING_RANK_BRACKET",
                          "FIFA_GROUP_BRACKET",
                          "GROUP_WINNERS_V_RUNNERS",
                          "UEFA_PLAYOFF",
                          "UEFA_R16",
                          "INTERCONTINENTAL_PATH"
                      ).contains(policy) && random.nextBoolean();
            var overseas = List.of("GUA", "GUY", "REU", "MTQ");
            if (
                policy.startsWith("OVERSEAS") &&
                ((home.region() != null && overseas.contains(home.region())) ||
                    (away.region() != null && overseas.contains(away.region())))
            ) {
                var t = home.region() != null && overseas.contains(home.region()) ? home : away;
                boolean hosts = List.of("GUY", "REU").contains(t.region()) == (rules.firstDate().getYear() % 2 == 0);
                switchHome = (home.id() == t.id()) != hosts;
            } else if (policy.equals("UEFA_FIXED_BRACKET")) switchHome = home.rank() < away.rank();
            else if (policy.startsWith("PRO_AMATEUR")) switchHome =
                professionalPot.contains(home.id()) != professionalPot.contains(away.id())
                    ? professionalPot.contains(home.id())
                    : random.nextBoolean();
            else if (policy.equals("AMATEUR_HOSTS")) switchHome =
                home.division() >= 3 && away.division() < 3
                    ? false
                    : away.division() >= 3 && home.division() < 3
                      ? true
                      : random.nextBoolean();
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

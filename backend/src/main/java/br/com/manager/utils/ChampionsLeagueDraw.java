package br.com.manager.utils;

import java.time.LocalDate;
import java.util.*;

/** Map clubs onto legal anonymous matchdays; no calendar edge-colouring at runtime. */
public final class ChampionsLeagueDraw {

    private ChampionsLeagueDraw() {}

    public record Club(long id, String association, int pot) {}

    public record Fixture(int round, LocalDate date, long home, long away) {}

    // Anonymous slot numbers, not catalogue club IDs. The validated eight matchdays
    // give each slot one home and one away opponent per pot and legal venue sequences.
    private static final int[][] SCHEDULE = {
        { 1, 1, 27 },
        { 1, 36, 33 },
        { 1, 4, 24 },
        { 1, 18, 12 },
        { 1, 20, 15 },
        { 1, 35, 9 },
        { 1, 32, 11 },
        { 1, 8, 16 },
        { 1, 28, 30 },
        { 1, 34, 5 },
        { 1, 19, 17 },
        { 1, 7, 23 },
        { 1, 2, 22 },
        { 1, 10, 14 },
        { 1, 25, 29 },
        { 1, 31, 13 },
        { 1, 3, 6 },
        { 1, 26, 21 },
        { 2, 14, 2 },
        { 2, 12, 7 },
        { 2, 13, 10 },
        { 2, 22, 3 },
        { 2, 21, 4 },
        { 2, 5, 19 },
        { 2, 6, 26 },
        { 2, 27, 20 },
        { 2, 9, 32 },
        { 2, 11, 31 },
        { 2, 23, 25 },
        { 2, 16, 1 },
        { 2, 24, 28 },
        { 2, 33, 35 },
        { 2, 17, 18 },
        { 2, 29, 34 },
        { 2, 30, 8 },
        { 2, 15, 36 },
        { 3, 24, 1 },
        { 3, 13, 33 },
        { 3, 3, 21 },
        { 3, 5, 4 },
        { 3, 12, 28 },
        { 3, 14, 27 },
        { 3, 17, 35 },
        { 3, 29, 18 },
        { 3, 30, 36 },
        { 3, 2, 7 },
        { 3, 9, 20 },
        { 3, 31, 6 },
        { 3, 26, 8 },
        { 3, 16, 25 },
        { 3, 10, 19 },
        { 3, 34, 32 },
        { 3, 11, 15 },
        { 3, 22, 23 },
        { 4, 4, 12 },
        { 4, 20, 5 },
        { 4, 8, 2 },
        { 4, 21, 13 },
        { 4, 34, 14 },
        { 4, 19, 22 },
        { 4, 15, 16 },
        { 4, 25, 26 },
        { 4, 18, 30 },
        { 4, 33, 24 },
        { 4, 35, 31 },
        { 4, 36, 10 },
        { 4, 23, 6 },
        { 4, 1, 11 },
        { 4, 32, 3 },
        { 4, 7, 29 },
        { 4, 27, 9 },
        { 4, 28, 17 },
        { 5, 6, 8 },
        { 5, 10, 34 },
        { 5, 1, 3 },
        { 5, 13, 26 },
        { 5, 28, 2 },
        { 5, 31, 25 },
        { 5, 18, 4 },
        { 5, 27, 33 },
        { 5, 12, 17 },
        { 5, 29, 21 },
        { 5, 22, 14 },
        { 5, 9, 5 },
        { 5, 30, 15 },
        { 5, 36, 7 },
        { 5, 24, 19 },
        { 5, 11, 20 },
        { 5, 35, 16 },
        { 5, 23, 32 },
        { 6, 5, 30 },
        { 6, 17, 23 },
        { 6, 20, 24 },
        { 6, 21, 27 },
        { 6, 33, 12 },
        { 6, 4, 1 },
        { 6, 26, 35 },
        { 6, 2, 31 },
        { 6, 14, 13 },
        { 6, 6, 28 },
        { 6, 3, 10 },
        { 6, 25, 18 },
        { 6, 32, 29 },
        { 6, 16, 11 },
        { 6, 19, 34 },
        { 6, 7, 9 },
        { 6, 8, 36 },
        { 6, 15, 22 },
        { 7, 13, 3 },
        { 7, 25, 2 },
        { 7, 10, 6 },
        { 7, 15, 8 },
        { 7, 11, 9 },
        { 7, 18, 24 },
        { 7, 16, 32 },
        { 7, 22, 36 },
        { 7, 20, 31 },
        { 7, 35, 23 },
        { 7, 33, 4 },
        { 7, 29, 1 },
        { 7, 5, 14 },
        { 7, 7, 17 },
        { 7, 30, 19 },
        { 7, 12, 21 },
        { 7, 34, 26 },
        { 7, 28, 27 },
        { 8, 1, 33 },
        { 8, 2, 13 },
        { 8, 3, 34 },
        { 8, 4, 35 },
        { 8, 17, 5 },
        { 8, 6, 18 },
        { 8, 19, 7 },
        { 8, 8, 25 },
        { 8, 9, 15 },
        { 8, 26, 10 },
        { 8, 23, 11 },
        { 8, 27, 12 },
        { 8, 14, 29 },
        { 8, 24, 16 },
        { 8, 36, 20 },
        { 8, 21, 30 },
        { 8, 32, 22 },
        { 8, 31, 28 },
    };

    public static List<Fixture> draw(List<Club> clubs, List<LocalDate> dates, long seed) {
        if (
            clubs.size() != 36 || dates.size() != 8 || clubs.stream().map(Club::id).distinct().count() != 36
        ) throw new IllegalArgumentException("A fase de liga exige 36 clubes e oito datas.");
        for (int pot = 1; pot <= 4; pot++) {
            int p = pot;
            if (
                clubs
                    .stream()
                    .filter(c -> c.pot() == p)
                    .count() != 9
            ) throw new IllegalArgumentException("Cada pote deve conter nove clubes.");
        }
        var base = new ArrayList<Fixture>();
        for (var f : SCHEDULE) base.add(new Fixture(f[0], dates.get(f[0] - 1), f[1], f[2]));
        for (int retry = 0; retry < 24; retry++) {
            // Feasibility depends on association/pot composition, never on a league's random seed.
            // Randomize only legal moves after finding the valid schedule.
            var template = variant(base, 37 + retry * 1009L);
            var mapper = new Mapper(clubs, template, new Random(37 + retry * 2017L));
            if (mapper.arrange()) {
                mapper.randomize(seed);
                return template
                    .stream()
                    .map(f ->
                        new Fixture(
                            f.round(),
                            dates.get(f.round() - 1),
                            clubs.get(mapper.assigned[(int) f.home() - 1]).id(),
                            clubs.get(mapper.assigned[(int) f.away() - 1]).id()
                        )
                    )
                    .toList();
            }
        }
        throw new IllegalStateException(
            "Não foi possível concluir um sorteio que respeite as restrições da UEFA. Potes: " +
                clubs
                    .stream()
                    .map(c -> c.pot() + ":" + c.association())
                    .toList()
        );
    }

    private static List<Fixture> variant(List<Fixture> base, long seed) {
        var result = new ArrayList<>(base);
        var random = new Random(seed);
        var linked = new boolean[37][37];
        for (var f : result) linked[(int) f.home()][(int) f.away()] = linked[(int) f.away()][(int) f.home()] = true;
        for (int attempt = 0; attempt < 2000; attempt++) {
            int first = random.nextInt(144),
                second = (first / 18) * 18 + random.nextInt(18);
            if (first == second) continue;
            var a = result.get(first);
            var b = result.get(second);
            if (
                (a.home() - 1) / 9 != (b.home() - 1) / 9 ||
                (a.away() - 1) / 9 != (b.away() - 1) / 9 ||
                a.home() == b.away() ||
                b.home() == a.away() ||
                linked[(int) a.home()][(int) b.away()] ||
                linked[(int) b.home()][(int) a.away()]
            ) continue;
            linked[(int) a.home()][(int) a.away()] = linked[(int) a.away()][(int) a.home()] = false;
            linked[(int) b.home()][(int) b.away()] = linked[(int) b.away()][(int) b.home()] = false;
            linked[(int) a.home()][(int) b.away()] = linked[(int) b.away()][(int) a.home()] = true;
            linked[(int) b.home()][(int) a.away()] = linked[(int) a.away()][(int) b.home()] = true;
            result.set(first, new Fixture(a.round(), a.date(), a.home(), b.away()));
            result.set(second, new Fixture(b.round(), b.date(), b.home(), a.away()));
        }
        return result;
    }

    private static final class Mapper {

        final List<Club> clubs;
        final List<Fixture> fixtures;
        final Random random;
        final int[] assigned = new int[36],
            country = new int[36];
        final List<Integer> order = new ArrayList<>();
        final boolean[] used = new boolean[36];
        final int[][] counts = new int[36][36];
        final List<List<Integer>> neighbours = new ArrayList<>();
        int nodes;

        Mapper(List<Club> clubs, List<Fixture> fixtures, Random random) {
            this.clubs = clubs;
            this.fixtures = fixtures;
            this.random = random;
            Arrays.fill(assigned, -1);
            var ids = new HashMap<String, Integer>();
            for (int i = 0; i < 36; i++) {
                neighbours.add(new ArrayList<>());
                country[i] = ids.computeIfAbsent(clubs.get(i).association(), k -> ids.size());
                order.add(i);
            }
            Collections.shuffle(order, random);
            for (var f : fixtures) {
                neighbours.get((int) f.home() - 1).add((int) f.away() - 1);
                neighbours.get((int) f.away() - 1).add((int) f.home() - 1);
            }
        }

        boolean arrange() {
            for (int pot = 1; pot <= 4; pot++) {
                int p = pot;
                var members = new ArrayList<>(
                    order
                        .stream()
                        .filter(i -> clubs.get(i).pot() == p)
                        .toList()
                );
                Collections.shuffle(members, random);
                for (int i = 0; i < 9; i++) assigned[(pot - 1) * 9 + i] = members.get(i);
            }
            for (int slot = 0; slot < 36; slot++) for (int other : neighbours.get(slot))
                counts[other][country[assigned[slot]]]++;
            int score = penalty();
            for (int step = 0; step < 60000 && score != 0; step++) {
                double temperature = 2.5 * (1.0 - (step % 6000) / 6000.0) + .08;
                if (step % 2 == 0) {
                    int first = random.nextInt(144),
                        second = (first / 18) * 18 + random.nextInt(18);
                    var x = fixtures.get(first);
                    var y = fixtures.get(second);
                    if (
                        first == second ||
                        (x.home() - 1) / 9 != (y.home() - 1) / 9 ||
                        (x.away() - 1) / 9 != (y.away() - 1) / 9 ||
                        x.home() == y.away() ||
                        y.home() == x.away() ||
                        neighbours.get((int) x.home() - 1).contains((int) y.away() - 1) ||
                        neighbours.get((int) y.home() - 1).contains((int) x.away() - 1)
                    ) continue;
                    rewire(first, second);
                    int next = penalty();
                    if (next <= score || random.nextDouble() < Math.exp((score - next) / temperature)) score = next;
                    else rewire(first, second);
                    continue;
                }
                int a = random.nextInt(36),
                    b = (a / 9) * 9 + random.nextInt(9);
                if (country[assigned[a]] == country[assigned[b]]) continue;
                swap(a, b);
                int next = penalty();
                if (next <= score || random.nextDouble() < Math.exp((score - next) / temperature)) score = next;
                else swap(a, b);
            }
            return score == 0;
        }

        void link(int a, int b, boolean add) {
            if (add) {
                neighbours.get(a).add(b);
                neighbours.get(b).add(a);
            } else {
                neighbours.get(a).remove(Integer.valueOf(b));
                neighbours.get(b).remove(Integer.valueOf(a));
            }
            counts[a][country[assigned[b]]] += add ? 1 : -1;
            counts[b][country[assigned[a]]] += add ? 1 : -1;
        }

        void randomize(long seed) {
            var shuffle = new Random(seed);
            for (int step = 0; step < 2000; step++) {
                int a = shuffle.nextInt(36),
                    b = (a / 9) * 9 + shuffle.nextInt(9);
                if (country[assigned[a]] == country[assigned[b]]) swap(a, b);
                int first = shuffle.nextInt(144),
                    second = (first / 18) * 18 + shuffle.nextInt(18);
                var x = fixtures.get(first);
                var y = fixtures.get(second);
                if (
                    first == second ||
                    (x.home() - 1) / 9 != (y.home() - 1) / 9 ||
                    (x.away() - 1) / 9 != (y.away() - 1) / 9 ||
                    x.home() == y.away() ||
                    y.home() == x.away() ||
                    neighbours.get((int) x.home() - 1).contains((int) y.away() - 1) ||
                    neighbours.get((int) y.home() - 1).contains((int) x.away() - 1)
                ) continue;
                rewire(first, second);
                if (penalty() != 0) rewire(first, second);
            }
        }

        void rewire(int first, int second) {
            var a = fixtures.get(first);
            var b = fixtures.get(second);
            link((int) a.home() - 1, (int) a.away() - 1, false);
            link((int) b.home() - 1, (int) b.away() - 1, false);
            link((int) a.home() - 1, (int) b.away() - 1, true);
            link((int) b.home() - 1, (int) a.away() - 1, true);
            fixtures.set(first, new Fixture(a.round(), a.date(), a.home(), b.away()));
            fixtures.set(second, new Fixture(b.round(), b.date(), b.home(), a.away()));
        }

        void swap(int a, int b) {
            int first = country[assigned[a]],
                second = country[assigned[b]];
            for (int other : neighbours.get(a)) {
                counts[other][first]--;
                counts[other][second]++;
            }
            for (int other : neighbours.get(b)) {
                counts[other][second]--;
                counts[other][first]++;
            }
            int club = assigned[a];
            assigned[a] = assigned[b];
            assigned[b] = club;
        }

        int penalty() {
            int result = 0;
            for (int slot = 0; slot < 36; slot++) {
                result += 8 * counts[slot][country[assigned[slot]]];
                for (int count : counts[slot]) if (count > 2) result += (count - 2) * (count - 2);
            }
            return result;
        }
    }
}

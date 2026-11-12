package br.com.manager.utils;

import br.com.manager.models.dto.PlayerAwardDtos.*;
import java.util.*;

/** Deterministic statistical continuation of the already resolved background score. Never changes its result. */
public final class BackgroundPlayerPerformance {

    private BackgroundPlayerPerformance() {}

    public static List<Performance> play(String save, Fixture game, List<RosterPlayer> home, List<RosterPlayer> away) {
        var random = new Random(((long) save.hashCode() << 32) ^ game.id() ^ 0x415741524453L);
        var result = new ArrayList<Performance>();
        team(result, game, lineup(home), game.home(), game.homeName(), game.homeGoals(), game.awayGoals(), random);
        team(result, game, lineup(away), game.away(), game.awayName(), game.awayGoals(), game.homeGoals(), random);
        return result;
    }

    private static List<RosterPlayer> lineup(List<RosterPlayer> roster) {
        var pool = roster
            .stream()
            .sorted(Comparator.comparingInt(RosterPlayer::ability).reversed().thenComparingLong(RosterPlayer::id))
            .toList();
        var selected = new ArrayList<RosterPlayer>();
        for (var slot : List.of(Map.entry("GOL", 1), Map.entry("DEF", 4), Map.entry("MEI", 3), Map.entry("ATA", 3)))
            pool.stream()
                .filter(p -> PlayerAwardRules.group(p.position()).equals(slot.getKey()))
                .limit(slot.getValue())
                .forEach(selected::add);
        for (var p : pool) if (selected.size() < 11 && !selected.contains(p)) selected.add(p);
        return selected;
    }

    private static RosterPlayer pick(List<RosterPlayer> team, Random random, boolean assist) {
        double total = team
                .stream()
                .mapToDouble(p -> weight(p, assist))
                .sum(),
            target = random.nextDouble() * total;
        for (var p : team) {
            target -= weight(p, assist);
            if (target < 0) return p;
        }
        return team.getLast();
    }

    private static double weight(RosterPlayer p, boolean assist) {
        return (
            Math.pow(p.ability() / 60.0, 1.5) *
            switch (PlayerAwardRules.group(p.position())) {
                case "GOL" -> .002;
                case "DEF" -> assist ? .4 : .25;
                case "MEI" -> assist ? 2.5 : 1.1;
                default -> assist ? 1.5 : 3;
            }
        );
    }

    private static void team(
        List<Performance> result,
        Fixture game,
        List<RosterPlayer> players,
        long team,
        String name,
        int goals,
        int conceded,
        Random random
    ) {
        if (players.isEmpty()) return;
        var scored = new HashMap<Long, Integer>();
        var assists = new HashMap<Long, Integer>();
        for (int i = 0; i < goals; i++) {
            var scorer = pick(players, random, false);
            scored.merge(scorer.id(), 1, Integer::sum);
            var helpers = players
                .stream()
                .filter(p -> p.id() != scorer.id())
                .toList();
            if (!helpers.isEmpty() && random.nextDouble() < .78) assists.merge(
                pick(helpers, random, true).id(),
                1,
                Integer::sum
            );
        }
        for (var p : players) {
            var group = PlayerAwardRules.group(p.position());
            int g = scored.getOrDefault(p.id(), 0),
                a = assists.getOrDefault(p.id(), 0);
            int saves = group.equals("GOL") ? random.nextInt(2, 7) : 0;
            int tackles = group.equals("DEF")
                ? random.nextInt(2, 8)
                : group.equals("MEI")
                  ? random.nextInt(1, 5)
                  : random.nextInt(0, 3);
            int passes = group.equals("GOL") ? random.nextInt(12, 30) : random.nextInt(25, 75);
            double rating =
                6.1 +
                (random.nextDouble() - .5) * 1.2 +
                (goals > conceded ? .35 : goals < conceded ? -.25 : 0) +
                g * .9 +
                a * .55 +
                (conceded == 0 && List.of("GOL", "DEF").contains(group) ? .6 : 0) +
                saves * .07 +
                tackles * .025;
            rating = Math.round(Math.clamp(rating, 3, 10) * 10) / 10.0;
            result.add(
                new Performance(
                    game.id(),
                    p.id(),
                    team,
                    p.name(),
                    name,
                    p.position(),
                    p.ability(),
                    game.date(),
                    90,
                    g,
                    a,
                    passes,
                    tackles,
                    saves,
                    conceded == 0 ? 1 : 0,
                    rating,
                    true
                )
            );
        }
    }
}

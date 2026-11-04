package br.com.manager.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** A reproducible draw; no scores or match simulation are generated here. */
public final class NationalLeagueDraw {

    private NationalLeagueDraw() {}

    public record Fixture(int round, LocalDate date, Long homeTeamId, Long awayTeamId) {}

    public static List<Fixture> draw(List<Long> teams, LocalDate firstDate, LocalDate lastDate, long seed) {
        if (
            teams == null ||
            teams.size() < 2 ||
            new HashSet<>(teams).size() != teams.size() ||
            teams.stream().anyMatch(Objects::isNull)
        ) throw new IllegalArgumentException("A competição exige pelo menos dois clubes distintos.");
        var rotation = new ArrayList<>(teams);
        Collections.shuffle(rotation, new Random(seed));
        if (rotation.size() % 2 != 0) rotation.add(null);
        int firstLegRounds = rotation.size() - 1,
            totalRounds = firstLegRounds * 2;
        long days = ChronoUnit.DAYS.between(firstDate, lastDate);
        if (days < (totalRounds - 1) * 3L) throw new IllegalArgumentException(
            "O período não comporta as rodadas com pelo menos três dias de intervalo."
        );
        var result = new ArrayList<Fixture>();
        for (int round = 0; round < firstLegRounds; round++) {
            for (int pair = 0; pair < rotation.size() / 2; pair++) {
                Long a = rotation.get(pair),
                    b = rotation.get(rotation.size() - 1 - pair);
                if (a == null || b == null) continue;
                boolean reverse = pair == 0 ? round % 2 == 1 : pair % 2 == 1;
                Long home = reverse ? b : a,
                    away = reverse ? a : b;
                result.add(new Fixture(round + 1, firstDate.plusDays((days * round) / (totalRounds - 1)), home, away));
                int returnRound = round + firstLegRounds;
                result.add(
                    new Fixture(
                        returnRound + 1,
                        firstDate.plusDays((days * returnRound) / (totalRounds - 1)),
                        away,
                        home
                    )
                );
            }
            rotation.add(1, rotation.removeLast());
        }
        result.sort(Comparator.comparingInt(Fixture::round).thenComparing(Fixture::homeTeamId));
        return List.copyOf(result);
    }
}

package br.com.manager.utils;

import br.com.manager.models.dto.PlayerAwardDtos.*;
import java.util.*;

public final class PlayerAwardRules {

    private PlayerAwardRules() {}

    public static final int MIN_RATED_MATCHES = 10,
        MIN_MINUTES = 600;

    public static String group(String position) {
        return switch (position) {
            case "GOL", "GK" -> "GOL";
            case "DEF", "ZAG", "LD", "LE" -> "DEF";
            case "MEI", "MC", "VOL", "ME", "MD" -> "MEI";
            default -> "ATA";
        };
    }

    private static double production(Candidate p) {
        double ninety = 90.0 / Math.max(90, p.minutes());
        return (
            ninety *
            switch (group(p.position())) {
                case "GOL" -> p.saves() * .25 + p.cleanSheets();
                case "DEF" -> p.tackles() * .3 + p.cleanSheets() + p.goals() + p.assists();
                case "MEI" -> p.assists() * 1.5 + p.goals() + p.passes() / 120.0 + p.tackles() * .1;
                default -> p.goals() * 1.5 + p.assists();
            }
        );
    }

    public static List<Finalist> goldenBall(List<Candidate> players) {
        var eligible = players
            .stream()
            .filter(p -> p.ratedAppearances() >= MIN_RATED_MATCHES && p.minutes() >= MIN_MINUTES)
            .toList();
        var maximum = new HashMap<String, Double>();
        eligible.forEach(p -> maximum.merge(group(p.position()), production(p), Math::max));
        var scores = new HashMap<Long, Double>();
        eligible.forEach(p ->
            scores.put(
                p.playerId(),
                p.averageRating() * 7.5 +
                    (maximum.get(group(p.position())) > 0
                        ? (20 * production(p)) / maximum.get(group(p.position()))
                        : 0) +
                    p.ability() * .05
            )
        );
        var ordered = eligible
            .stream()
            .sorted(
                Comparator.comparingDouble((Candidate p) -> scores.get(p.playerId()))
                    .reversed()
                    .thenComparing(Comparator.comparingDouble(Candidate::averageRating).reversed())
                    .thenComparing(Comparator.comparingInt((Candidate p) -> p.goals() + p.assists()).reversed())
                    .thenComparingLong(Candidate::playerId)
            )
            .limit(3)
            .toList();
        var result = new ArrayList<Finalist>();
        for (int i = 0; i < ordered.size(); i++) result.add(
            new Finalist(i + 1, ordered.get(i), scores.get(ordered.get(i).playerId()))
        );
        return result;
    }

    public static List<Finalist> scorers(List<Candidate> players) {
        var ordered = players
            .stream()
            .filter(p -> p.goals() > 0)
            .sorted(
                Comparator.comparingInt(Candidate::goals)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(Candidate::assists).reversed())
                    .thenComparing(Comparator.comparingDouble(Candidate::averageRating).reversed())
                    .thenComparingInt(Candidate::minutes)
                    .thenComparingLong(Candidate::playerId)
            )
            .limit(3)
            .toList();
        var result = new ArrayList<Finalist>();
        for (int i = 0; i < ordered.size(); i++) result.add(
            new Finalist(i + 1, ordered.get(i), ordered.get(i).goals())
        );
        return result;
    }
}

package br.com.manager.models.dto;

import java.time.LocalDate;
import java.util.List;

public final class PlayerAwardDtos {

    private PlayerAwardDtos() {}

    public record Performance(
        long fixtureId,
        long playerId,
        long teamId,
        String playerName,
        String teamName,
        String position,
        int ability,
        LocalDate date,
        int minutes,
        int goals,
        int assists,
        int passes,
        int tackles,
        int saves,
        int cleanSheets,
        Double rating,
        boolean estimated
    ) {}

    public record Candidate(
        long playerId,
        String name,
        long teamId,
        String teamName,
        String position,
        int ability,
        int appearances,
        int ratedAppearances,
        int minutes,
        int goals,
        int assists,
        int passes,
        int tackles,
        int saves,
        int cleanSheets,
        double averageRating,
        int estimatedMatches
    ) {}

    public record Finalist(int place, Candidate player, double score) {}

    public record Ceremony(
        long id,
        String key,
        String scope,
        int year,
        String title,
        String competition,
        LocalDate date,
        int trophyId,
        String criteria,
        List<Finalist> finalists
    ) {}

    public record Competition(
        String scope,
        String name,
        int year,
        LocalDate finishedOn,
        Long seasonId,
        Long tournamentId,
        boolean relevant
    ) {}

    public record Fixture(
        long id,
        LocalDate date,
        long home,
        long away,
        String homeName,
        String awayName,
        int homeGoals,
        int awayGoals
    ) {}

    public record RosterPlayer(long id, long team, String name, String position, int ability) {}
}

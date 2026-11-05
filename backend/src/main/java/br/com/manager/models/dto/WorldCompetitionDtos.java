package br.com.manager.models.dto;

import java.time.LocalDate;
import java.util.List;

public final class WorldCompetitionDtos {

    private WorldCompetitionDtos() {}

    public record Fixture(
        long id,
        long seasonId,
        LocalDate date,
        long home,
        long away,
        int homeRating,
        int awayRating
    ) {}

    public record Competition(
        long id,
        long leagueId,
        int year,
        String label,
        String name,
        String country,
        int division,
        int teams,
        int played,
        int scheduled,
        LocalDate firstDate,
        LocalDate lastDate,
        boolean historical,
        String sourceUrl
    ) {}

    public record Standing(
        long teamId,
        String name,
        int played,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        int points,
        String groupName
    ) {}

    public record Championship(Competition competition, List<Standing> table, List<NationalFixtureDto> fixtures) {}

    public record Overview(LocalDate date, List<Competition> competitions) {}
}

package br.com.manager.models.dto;

import br.com.manager.utils.TournamentRulebooks.Rulebook;
import java.time.LocalDate;
import java.util.List;

public final class TournamentDtos {

    private TournamentDtos() {}

    public record Edition(
        long id,
        String key,
        int year,
        String label,
        String name,
        String country,
        String status,
        int currentStage,
        Long championId,
        Long runnerUpId,
        Rulebook rules
    ) {}

    public record Entrant(
        long id,
        String name,
        String stage,
        int rank,
        String association,
        String region,
        int division,
        int rating
    ) {}

    public record Tie(int stage, int number, long home, long away, Long winner) {}

    public record Game(
        long id,
        long edition,
        int stage,
        int number,
        int leg,
        LocalDate date,
        long home,
        long away,
        int homeRating,
        int awayRating,
        Integer homeGoals,
        Integer awayGoals,
        String status
    ) {}

    public record Summary(Edition edition, List<Entrant> teams, List<Tie> ties, List<Game> fixtures) {}
}

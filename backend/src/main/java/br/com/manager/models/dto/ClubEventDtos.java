package br.com.manager.models.dto;

import java.time.LocalDate;
import java.util.List;

public final class ClubEventDtos {

    private ClubEventDtos() {}

    public record Venue(
        String key,
        String name,
        String country,
        double latitude,
        double longitude,
        long dailyCost,
        int fatigue,
        List<String> focus,
        String description
    ) {}

    public record Kind(String key, String name, String description, int cooldownDays, boolean annual) {}

    public record Opponent(long id, String name, int rating, boolean human, long fee) {}

    public record Booking(
        long id,
        long teamId,
        String teamName,
        Long opponentId,
        String opponentName,
        String type,
        LocalDate date,
        LocalDate endDate,
        String venue,
        long cost,
        String status,
        Long fixtureId,
        int processedDays,
        long revenue,
        String details
    ) {}

    public record Options(
        long teamId,
        LocalDate currentDate,
        LocalDate foundationDate,
        LocalDate nextAnniversary,
        LocalDate seasonEnd,
        long cash,
        int fanTrust,
        int squadTrust,
        List<Kind> kinds,
        List<Venue> venues,
        List<Opponent> opponents,
        List<Booking> bookings
    ) {}

    public record Quote(LocalDate date, LocalDate endDate, long cost, String description) {}
}

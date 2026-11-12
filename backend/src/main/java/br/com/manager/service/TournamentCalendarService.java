package br.com.manager.service;

import br.com.manager.repository.TournamentCalendarRepository;
import br.com.manager.repository.TournamentCalendarRepository.Booking;
import br.com.manager.utils.NationalLeagueDraw.Fixture;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TournamentCalendarService {

    private final TournamentCalendarRepository repository;

    public TournamentCalendarService(TournamentCalendarRepository repository) {
        this.repository = repository;
    }

    private Collection<Long> teams(List<Fixture> games) {
        var teams = new HashSet<Long>();
        games.forEach(g -> {
            teams.add(g.homeTeamId());
            teams.add(g.awayTeamId());
        });
        return teams;
    }

    private boolean clash(Booking b, long home, long away, LocalDate date) {
        return (
            (b.home() == home || b.home() == away || b.away() == home || b.away() == away) &&
            Math.abs(ChronoUnit.DAYS.between(b.date(), date)) < 3
        );
    }

    public List<Fixture> plan(String id, List<Fixture> games) {
        if (games.isEmpty()) return games;
        var bookings = new ArrayList<>(repository.bookings(id, teams(games)));
        var humanDates = repository.humanDates(id);
        var initial = repository.start(id);
        var result = new ArrayList<Fixture>();
        for (var game : games) {
            var date = game.date();
            if (!date.isBefore(initial)) for (long club : List.of(game.homeTeamId(), game.awayTeamId())) {
                var current = humanDates.get(club);
                if (current != null && date.isBefore(current)) date = current;
            }
            for (int attempt = 0; attempt < 365; attempt++) {
                LocalDate candidate = date;
                boolean occupied = bookings
                    .stream()
                    .anyMatch(
                        b ->
                            (b.cup() || b.status().equals("PLAYED")) &&
                            clash(b, game.homeTeamId(), game.awayTeamId(), candidate)
                    );
                if (!occupied) break;
                date = date.plusDays(1);
                if (attempt == 364) throw new IllegalStateException("Não foi possível reservar a data da copa.");
            }
            result.add(new Fixture(game.round(), date, game.homeTeamId(), game.awayTeamId()));
            bookings.add(new Booking(-1, game.homeTeamId(), game.awayTeamId(), date, true, "SCHEDULED"));
        }
        return result;
    }

    public void protectNationals(String id, List<Fixture> games) {
        if (games.isEmpty()) return;
        var bookings = new ArrayList<>(repository.bookings(id, teams(games)));
        for (int i = 0; i < bookings.size(); i++) {
            var booking = bookings.get(i);
            if (booking.cup() || !booking.status().equals("SCHEDULED")) continue;
            if (games.stream().noneMatch(g -> clash(booking, g.homeTeamId(), g.awayTeamId(), g.date()))) continue;
            LocalDate date = booking.date();
            for (int attempt = 0; attempt < 365; attempt++) {
                date = date.plusDays(1);
                LocalDate candidate = date;
                if (
                    bookings
                        .stream()
                        .noneMatch(b -> b.id() != booking.id() && clash(b, booking.home(), booking.away(), candidate))
                ) break;
                if (attempt == 364) throw new IllegalStateException("Não foi possível reagendar o nacional.");
            }
            repository.move(id, booking.id(), date);
            bookings.set(i, new Booking(booking.id(), booking.home(), booking.away(), date, false, "SCHEDULED"));
        }
    }
}

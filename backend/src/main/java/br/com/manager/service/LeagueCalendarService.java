package br.com.manager.service;

import br.com.manager.models.dto.CalendarEventDto;
import br.com.manager.models.responses.LeagueCalendarResponse;
import br.com.manager.repository.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueCalendarService {

    private final SaveService saves;
    private final LeagueCalendarRepository calendar;
    private final ManagementRepository management;
    private final NationalSeasonService seasons;
    private final NationalSeasonRepository fixtures;
    private final TournamentService tournaments;

    public LeagueCalendarService(
        SaveService saves,
        LeagueCalendarRepository calendar,
        ManagementRepository management,
        NationalSeasonService seasons,
        NationalSeasonRepository fixtures,
        TournamentService tournaments
    ) {
        this.saves = saves;
        this.calendar = calendar;
        this.management = management;
        this.seasons = seasons;
        this.fixtures = fixtures;
        this.tournaments = tournaments;
    }

    @Transactional(readOnly = true)
    public br.com.manager.models.responses.SeasonCalendarResponse season(String id, String token) {
        var week = week(id, token);
        var member = saves.realtimeMember(id, token.substring(7));
        var available = fixtures.seasons(id, member.teamId());
        var current = available
            .stream()
            .filter(s -> !s.lastDate().isBefore(week.currentDate()))
            .min(Comparator.comparing(br.com.manager.models.dto.NationalSeasonDto::firstDate))
            .orElse(available.isEmpty() ? null : available.getFirst());
        var first = current == null ? week.currentDate().withDayOfYear(1) : current.firstDate().withDayOfMonth(1);
        if (first.isAfter(week.currentDate())) first = week.currentDate().withDayOfMonth(1);
        var last = current == null ? LocalDate.of(week.currentDate().getYear(), 12, 31) : current.lastDate();
        // Include the rest of the final month for club events after the last national fixture.
        last = last.with(TemporalAdjusters.lastDayOfMonth());
        if (last.isBefore(week.currentDate())) last = LocalDate.of(week.currentDate().getYear(), 12, 31);
        var events = new ArrayList<>(calendar.events(id, member.teamId(), first, last));
        events.addAll(fixtures.week(id, member.teamId(), first, last));
        events.addAll(tournaments.drawEvents(id, first, last));
        events.addAll(
            week
                .events()
                .stream()
                .filter(e -> e.type().equals("PREPARATION"))
                .toList()
        );
        var start = calendar.start(id);
        for (var player : management.players(id, member.teamId())) {
            var state = player.condition();
            if (state.injuryName() == null || state.returnRound() == null) continue;
            var recovery = start.plusDays(state.returnRound() - 1L);
            if (
                !recovery.isBefore(week.currentDate()) && !recovery.isBefore(first) && !recovery.isAfter(last)
            ) events.add(
                new CalendarEventDto(
                    "recovery-" + player.id(),
                    recovery,
                    "RECOVERY",
                    "Retorno previsto: " + player.name(),
                    "Previsão de retorno após " + state.injuryName() + ".",
                    "SCHEDULED"
                )
            );
        }
        events.sort(Comparator.comparing(CalendarEventDto::date).thenComparing(CalendarEventDto::id));
        return new br.com.manager.models.responses.SeasonCalendarResponse(
            week.currentDate(),
            first,
            last,
            current == null ? String.valueOf(week.currentDate().getYear()) : current.label(),
            List.copyOf(events)
        );
    }

    @Transactional(readOnly = true)
    public LeagueCalendarResponse week(String id, String token) {
        var room = saves.summary(id, token);
        var member = saves.realtimeMember(id, token.substring(7));
        var start = calendar.start(id);
        var date = start.plusDays(Math.max(0, fixtures.teamDay(id, member.teamId()) - 1L));
        var monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var sunday = monday.plusDays(6);
        var events = new ArrayList<>(calendar.events(id, member.teamId(), monday, sunday));
        events.addAll(fixtures.week(id, member.teamId(), monday, sunday));
        events.addAll(tournaments.drawEvents(id, monday, sunday));
        long ready = room
            .members()
            .stream()
            .filter(m -> m.ready())
            .count();
        events.add(
            new CalendarEventDto(
                "preparation",
                date,
                "PREPARATION",
                "Preparação da rodada " + room.roundNumber(),
                ready + " de " + room.capacity() + " participantes prontos.",
                room.status().equals("READY") ? "READY" : "WAITING"
            )
        );
        for (var player : management.players(id, member.teamId())) {
            var state = player.condition();
            if (state.injuryName() == null || state.returnRound() == null) continue;
            var recovery = start.plusDays(state.returnRound() - 1L);
            if (!recovery.isBefore(date) && !recovery.isAfter(sunday)) events.add(
                new CalendarEventDto(
                    "recovery-" + player.id(),
                    recovery,
                    "RECOVERY",
                    "Retorno previsto: " + player.name(),
                    "Previsão de retorno após " + state.injuryName() + ". Rodada " + state.returnRound() + ".",
                    "SCHEDULED"
                )
            );
        }
        events.sort(Comparator.comparing(CalendarEventDto::date).thenComparing(CalendarEventDto::id));
        return new LeagueCalendarResponse(date, monday, sunday, List.copyOf(events));
    }
}

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

    public LeagueCalendarService(
        SaveService saves,
        LeagueCalendarRepository calendar,
        ManagementRepository management,
        NationalSeasonService seasons,
        NationalSeasonRepository fixtures
    ) {
        this.saves = saves;
        this.calendar = calendar;
        this.management = management;
        this.seasons = seasons;
        this.fixtures = fixtures;
    }

    @Transactional
    public LeagueCalendarResponse week(String id, String token) {
        var room = saves.authorizedRoom(id, token);
        var member = saves.realtimeMember(id, token.substring(7));
        var start = calendar.start(id);
        var date = start.plusDays(Math.max(0, room.roundNumber() - 1L));
        seasons.ensureForDate(id, date);
        var monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var sunday = monday.plusDays(6);
        var events = new ArrayList<>(calendar.events(id, member.teamId(), monday, sunday));
        events.addAll(fixtures.week(id, member.teamId(), monday, sunday));
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

package br.com.manager.models.responses;

import br.com.manager.models.dto.CalendarEventDto;
import java.time.LocalDate;
import java.util.List;

public record LeagueCalendarResponse(
    LocalDate currentDate,
    LocalDate weekStart,
    LocalDate weekEnd,
    List<CalendarEventDto> events
) {}

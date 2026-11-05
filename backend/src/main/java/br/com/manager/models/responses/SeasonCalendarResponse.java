package br.com.manager.models.responses;

import br.com.manager.models.dto.CalendarEventDto;
import java.time.LocalDate;
import java.util.List;

public record SeasonCalendarResponse(
    LocalDate currentDate,
    LocalDate seasonStart,
    LocalDate seasonEnd,
    String seasonLabel,
    List<CalendarEventDto> events
) {}

package br.com.manager.models.responses;

import br.com.manager.models.dto.CalendarEventDto;
import java.time.LocalDate;
import java.util.List;

public record DayProgressResponse(
    LocalDate currentDate,
    LocalDate nextMatch,
    int advancedDays,
    String stopReason,
    List<CalendarEventDto> events
) {}

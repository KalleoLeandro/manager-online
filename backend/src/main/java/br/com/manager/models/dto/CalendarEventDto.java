package br.com.manager.models.dto;

import java.time.LocalDate;

public record CalendarEventDto(
    String id,
    LocalDate date,
    String type,
    String title,
    String description,
    String status
) {}

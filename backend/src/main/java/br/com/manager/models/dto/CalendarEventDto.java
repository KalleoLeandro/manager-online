package br.com.manager.models.dto;

import java.time.LocalDate;

public record CalendarEventDto(
    String id,
    LocalDate date,
    String type,
    String title,
    String description,
    String status,
    Long opponentTeamId,
    String opponentTeamName,
    String venue
) {
    public CalendarEventDto(String id, LocalDate date, String type, String title, String description, String status) {
        this(id,date,type,title,description,status,null,null,null);
    }
}

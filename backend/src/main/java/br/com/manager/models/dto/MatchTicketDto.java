package br.com.manager.models.dto;

import java.time.LocalDate;

public record MatchTicketDto(long fixtureId, LocalDate date, String competition, long homeId, String homeName,
    String homeColor, String homeSecondary, long awayId, String awayName, String awayColor, String awaySecondary) {}

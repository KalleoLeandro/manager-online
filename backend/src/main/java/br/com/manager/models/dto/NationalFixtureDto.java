package br.com.manager.models.dto;

import java.time.LocalDate;

public record NationalFixtureDto(
    Long id,
    int round,
    LocalDate date,
    Long homeTeamId,
    String homeTeamName,
    Long awayTeamId,
    String awayTeamName,
    String status,
    Integer homeGoals,
    Integer awayGoals
) {}

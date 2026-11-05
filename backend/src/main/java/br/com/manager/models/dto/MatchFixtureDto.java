package br.com.manager.models.dto;

import java.time.LocalDate;

public record MatchFixtureDto(
    long id,
    long seasonId,
    LocalDate date,
    long homeId,
    String homeName,
    long awayId,
    String awayName
) {}

package br.com.manager.models.dto;

import java.time.LocalDate;

public record NationalSeasonDto(
    Long id,
    int year,
    String label,
    String competitionName,
    String country,
    int division,
    int teamCount,
    int rounds,
    LocalDate firstDate,
    LocalDate lastDate,
    String format
) {}

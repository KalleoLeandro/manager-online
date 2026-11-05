package br.com.manager.models.dto;

public record MatchSummaryDto(
    long fixtureId,
    long homeId,
    String homeName,
    long awayId,
    String awayName,
    int homeGoals,
    int awayGoals,
    int minute,
    int period,
    String status,
    boolean humanVsHuman,
    long revision
) {}

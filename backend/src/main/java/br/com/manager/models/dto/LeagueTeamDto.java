package br.com.manager.models.dto;

public record LeagueTeamDto(
    Long id,
    Long leagueId,
    String leagueName,
    String country,
    int division,
    String name,
    String shortName,
    String crestUrl,
    int rating,
    int playerCount,
    String managerName
) {}

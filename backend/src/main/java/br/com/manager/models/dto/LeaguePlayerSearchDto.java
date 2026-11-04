package br.com.manager.models.dto;

public record LeaguePlayerSearchDto(ManagedPlayerDto player, Long teamId, String teamName) {}

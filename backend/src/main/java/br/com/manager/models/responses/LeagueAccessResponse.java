package br.com.manager.models.responses;

public record LeagueAccessResponse(LeagueRoomResponse room, String memberId, String accessToken) {}

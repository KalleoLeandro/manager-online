package br.com.manager.models.dto;

import java.time.Instant;

public record LeagueRecordDto(
    String id,
    String name,
    String inviteCode,
    int capacity,
    String status,
    String catalogVersion,
    int roundNumber,
    Instant lastActivityAt
) {}

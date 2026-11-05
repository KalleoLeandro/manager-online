package br.com.manager.models.responses;

import br.com.manager.models.dto.LeagueMemberDto;
import java.time.Instant;
import java.util.List;

public record LeagueRoomResponse(
    String id,
    String name,
    String inviteCode,
    int capacity,
    String status,
    String catalogVersion,
    int roundNumber,
    List<LeagueMemberDto> members,
    Instant lastActivityAt,
    Instant inactivityExpiresAt,
    boolean localMultiplayer
) {}

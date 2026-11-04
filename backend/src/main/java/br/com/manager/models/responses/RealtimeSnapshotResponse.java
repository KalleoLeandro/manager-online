package br.com.manager.models.responses;

import br.com.manager.models.dto.PresenceDto;
import java.util.List;

public record RealtimeSnapshotResponse(
    String type,
    long revision,
    LeagueRoomResponse room,
    List<PresenceDto> presence,
    long serverTime
) {}

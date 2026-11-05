package br.com.manager.models.responses;

import br.com.manager.models.dto.ManagedPlayerDto;
import br.com.manager.models.dto.TeamPaletteDto;
import java.util.List;

public record TeamManagementResponse(
    LeagueRoomResponse room,
    String memberId,
    Long teamId,
    String teamName,
    String crestUrl,
    String leagueName,
    int teamRating,
    long budget,
    int points,
    TeamPaletteDto palette,
    List<ManagedPlayerDto> players,
    String formation,
    String mentality,
    List<Long> lineup,
    int version,
    boolean introductionSeen,
    br.com.manager.models.dto.TacticalPlanDto tacticalPlan
) {}

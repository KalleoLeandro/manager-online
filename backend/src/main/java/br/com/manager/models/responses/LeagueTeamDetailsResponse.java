package br.com.manager.models.responses;

import br.com.manager.models.dto.LeagueTeamDto;
import br.com.manager.models.dto.ManagedPlayerDto;
import java.util.List;

public record LeagueTeamDetailsResponse(LeagueTeamDto team, List<ManagedPlayerDto> players) {}

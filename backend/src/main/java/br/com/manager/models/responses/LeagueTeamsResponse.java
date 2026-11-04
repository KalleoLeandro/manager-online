package br.com.manager.models.responses;

import br.com.manager.models.dto.LeagueTeamDto;
import java.util.List;

public record LeagueTeamsResponse(Long ownTeamId, Long ownLeagueId, List<LeagueTeamDto> teams) {}

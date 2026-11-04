package br.com.manager.models.responses;

import br.com.manager.models.dto.CatalogLeagueDto;
import br.com.manager.models.dto.CatalogTeamDto;
import br.com.manager.models.dto.RosterPolicyDto;
import java.util.List;

public record CatalogResponse(
    String version,
    String source,
    String ratingMethod,
    List<CatalogLeagueDto> leagues,
    List<CatalogTeamDto> teams,
    List<RosterPolicyDto> rosterPolicies,
    long internationalTeams
) {}

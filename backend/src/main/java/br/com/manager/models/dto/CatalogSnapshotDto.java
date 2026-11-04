package br.com.manager.models.dto;

import java.util.List;

public record CatalogSnapshotDto(
    String version,
    String source,
    String ratingMethod,
    List<CatalogLeagueDto> leagues,
    List<CatalogTeamDto> teams,
    List<CatalogPlayerDto> players,
    List<String> warnings,
    List<RosterPolicyDto> rosterPolicies
) {}

package br.com.manager.models.responses;

import br.com.manager.models.dto.*;
import java.util.List;

public record NationalChampionshipResponse(
    Long ownTeamId,
    NationalSeasonDto season,
    List<NationalSeasonDto> seasons,
    List<NationalFixtureDto> fixtures
) {}

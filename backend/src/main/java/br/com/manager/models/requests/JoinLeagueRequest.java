package br.com.manager.models.requests;

import jakarta.validation.constraints.*;

public record JoinLeagueRequest(
    @NotBlank @Size(min = 2, max = 40) String managerName,
    @NotNull @Positive Long teamId
) {}

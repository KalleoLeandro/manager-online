package br.com.manager.models.requests;

import jakarta.validation.constraints.*;

public record CreateLeagueRequest(
    @NotBlank @Size(min = 2, max = 60) String name,
    @Min(1) @Max(20) int capacity,
    @NotBlank @Size(min = 2, max = 40) String managerName,
    @NotNull @Positive Long teamId,
    boolean localMultiplayer
) {
    public CreateLeagueRequest(String name, int capacity, String managerName, Long teamId) {
        this(name, capacity, managerName, teamId, false);
    }
}

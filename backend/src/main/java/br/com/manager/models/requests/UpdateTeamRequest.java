package br.com.manager.models.requests;

import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateTeamRequest(
    @NotBlank @Pattern(regexp = "4-4-2|4-3-3|4-5-1|3-5-2") String formation,
    @NotBlank @Pattern(regexp = "BALANCED|ATTACKING|DEFENSIVE") String mentality,
    @NotNull @Size(min = 11, max = 11) List<@NotNull @Positive Long> lineup,
    @Min(0) int version
) {}

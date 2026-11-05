package br.com.manager.models.requests;

import br.com.manager.models.dto.TacticalPlanDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateTeamRequest(
    @NotBlank @Pattern(regexp = "4-3-3|4-4-2|3-5-2|4-5-1|4-2-3-1|4-1-4-1|4-3-1-2|4-1-2-1-2|3-4-3|3-4-2-1|5-3-2|5-4-1") String formation,
    @NotBlank @Pattern(regexp = "BALANCED|ATTACKING|DEFENSIVE") String mentality,
    @NotNull @Size(min = 11, max = 11) List<@NotNull @Positive Long> lineup,
    @Min(0) int version,
    @Valid TacticalPlanDto tacticalPlan
) {
    public UpdateTeamRequest(String formation, String mentality, List<Long> lineup, int version) {
        this(formation, mentality, lineup, version, null);
    }
}

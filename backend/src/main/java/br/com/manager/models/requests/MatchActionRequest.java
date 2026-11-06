package br.com.manager.models.requests;

import br.com.manager.models.dto.TacticalPlanDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record MatchActionRequest(
    @NotBlank
    @Pattern(regexp = "SUBSTITUTE|SWAP_POSITIONS|TALK_PRAISE|TALK_ENCOURAGE|TALK_CRITICIZE|SET_KEEPER|TACTICS|PENALTY_TAKER|OPEN_TACTICS|PAUSE|RESUME|ENCOURAGE|DEMAND|CALM|COMPLAIN")
    String type,
    @Positive Long playerOut,
    @Positive Long playerIn,
    @Pattern(regexp = "BALANCED|ATTACKING|DEFENSIVE") String mentality,
    @Pattern(regexp = "4-3-3|4-4-2|3-5-2|4-5-1|4-2-3-1|4-1-4-1|4-3-1-2|4-1-2-1-2|3-4-3|3-4-2-1|5-3-2|5-4-1") String formation,
    @Min(0) long revision,
    @Valid TacticalPlanDto tacticalPlan
) {
    public MatchActionRequest(
        String type,
        Long playerOut,
        Long playerIn,
        String mentality,
        String formation,
        long revision
    ) {
        this(type, playerOut, playerIn, mentality, formation, revision, null);
    }
}

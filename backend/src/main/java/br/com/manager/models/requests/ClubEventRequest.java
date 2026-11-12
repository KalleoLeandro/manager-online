package br.com.manager.models.requests;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ClubEventRequest(
    @NotBlank
    @Pattern(regexp = "FRIENDLY|CAMP|CHARITY|MARKETING|YOUTH_DAY|ANNIVERSARY|TEAM_PARTY|SEASON_PARTY|PLAYER_AWARDS")
    String type,
    @NotNull LocalDate date,
    @Min(1) @Max(7) int days,
    @Positive Long opponentId,
    @Size(max = 40) String venue,
    LocalDate foundationDate
) {}

package br.com.manager.models.requests;

import jakarta.validation.constraints.*;

public record TransferOfferRequest(
    @Positive long playerId,
    @Min(0) @Max(1000000000000L) long fee,
    @Positive @Max(1000000000000L) long salaryMonthly,
    @Min(6) @Max(60) int months,
    @Min(0) @Max(1000000000000L) long signingBonus,
    @Min(0) @Max(1000000000000L) long appearanceBonus,
    @Min(0) @Max(1000000000000L) long goalBonus,
    @Min(0) @Max(1000000000000L) long titleBonus
) {}

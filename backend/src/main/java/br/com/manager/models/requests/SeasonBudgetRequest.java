package br.com.manager.models.requests;

import jakarta.validation.constraints.*;

public record SeasonBudgetRequest(
    @Positive long seasonId,
    @Min(1) int promisedRank,
    @Min(0) @Max(1000000000000L) long transferBudget,
    @Positive @Max(1000000000000L) long salaryBudget,
    @Min(0) @Max(1000000000000L) long infrastructureBudget,
    @Min(0) @Max(1000000000000L) long miscellaneousBudget,
    @Positive @Max(1000000000000L) long reserveBudget
) {}

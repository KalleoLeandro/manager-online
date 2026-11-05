package br.com.manager.models.requests;

import jakarta.validation.constraints.*;

public record TransferDecisionRequest(@NotNull Boolean accept, @Min(0) int revision) {}

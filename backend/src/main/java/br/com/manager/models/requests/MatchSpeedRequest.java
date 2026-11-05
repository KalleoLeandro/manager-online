package br.com.manager.models.requests;

import jakarta.validation.constraints.*;

public record MatchSpeedRequest(@NotNull @Min(50) @Max(400) Integer percent) {}

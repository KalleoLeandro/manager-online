package br.com.manager.models.dto;

import jakarta.validation.constraints.*;

public record PitchPositionDto(
    @NotNull @DecimalMin("0") @DecimalMax("100") Double x,
    @NotNull @DecimalMin("0") @DecimalMax("100") Double y
) {}

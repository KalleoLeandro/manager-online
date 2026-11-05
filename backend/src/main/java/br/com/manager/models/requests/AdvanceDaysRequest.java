package br.com.manager.models.requests;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record AdvanceDaysRequest(@NotNull LocalDate expectedDate, @Min(1) @Max(7) int days) {}

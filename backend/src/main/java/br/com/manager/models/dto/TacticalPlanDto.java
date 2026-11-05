package br.com.manager.models.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public class TacticalPlanDto {

    @Min(0)
    @Max(100)
    public int passingLength = 50;

    @Min(0)
    @Max(100)
    public int passingRisk = 50;

    @Min(0)
    @Max(100)
    public int tempo = 50;

    @Min(0)
    @Max(100)
    public int width = 50;

    @Min(0)
    @Max(100)
    public int defensiveLine = 50;

    @Min(0)
    @Max(100)
    public int pressing = 50;

    public boolean counterAttack, offsideTrap;

    @NotNull
    @Size(max = 11)
    @Valid
    public Map<@Positive Long, @NotNull @Valid PitchPositionDto> positions = new LinkedHashMap<>();
}

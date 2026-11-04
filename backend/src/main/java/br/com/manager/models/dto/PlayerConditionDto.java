package br.com.manager.models.dto;

public record PlayerConditionDto(
    int fitness,
    int effectiveAbility,
    boolean available,
    String status,
    String injuryName,
    Integer returnRound,
    int morale,
    int sharpness,
    double performanceFactor
) {}

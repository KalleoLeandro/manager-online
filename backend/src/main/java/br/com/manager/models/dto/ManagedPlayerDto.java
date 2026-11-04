package br.com.manager.models.dto;

public record ManagedPlayerDto(
    Long id,
    String name,
    String position,
    String level,
    int ability,
    String attribute1,
    int value1,
    String attribute2,
    int value2,
    Integer age,
    String number,
    String photoUrl,
    int fitness,
    boolean generated,
    String generationOrigin,
    String nationality,
    PlayerTechnicalDto technical,
    PlayerConditionDto condition
) {}

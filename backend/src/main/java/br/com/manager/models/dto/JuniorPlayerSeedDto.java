package br.com.manager.models.dto;

public record JuniorPlayerSeedDto(
    int slot,
    String name,
    String position,
    int ability,
    int age,
    PlayerTechnicalDto technical,
    br.com.manager.models.enums.YouthTalent talent
) {}

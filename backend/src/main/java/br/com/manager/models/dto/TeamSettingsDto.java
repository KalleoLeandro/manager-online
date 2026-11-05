package br.com.manager.models.dto;

public record TeamSettingsDto(String formation, String mentality, int version, TacticalPlanDto tacticalPlan) {
    public TeamSettingsDto(String formation, String mentality, int version) {
        this(formation, mentality, version, new TacticalPlanDto());
    }
}

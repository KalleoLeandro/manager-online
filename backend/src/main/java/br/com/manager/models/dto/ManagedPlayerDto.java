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
) {
    @com.fasterxml.jackson.annotation.JsonProperty("positions")
    public java.util.List<String> positions() {
        return br.com.manager.utils.PlayerPositionRules.preferred(position,technical==null?java.util.Map.of():technical.attributes());
    }
    @com.fasterxml.jackson.annotation.JsonProperty("positionsEstimated")
    public boolean positionsEstimated() { return !position.equals("GOL"); }
}

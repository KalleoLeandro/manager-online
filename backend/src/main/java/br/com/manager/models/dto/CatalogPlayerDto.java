package br.com.manager.models.dto;

public record CatalogPlayerDto(
    Long id,
    Long teamId,
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
    String nationality,
    int appearances,
    int goals,
    int assists,
    int minutes,
    String sourceUrl,
    boolean generated,
    String generationOrigin,
    PlayerTechnicalDto technical
) {
    @com.fasterxml.jackson.annotation.JsonProperty("positions")
    public java.util.List<String> positions(){return br.com.manager.utils.PlayerPositionRules.preferred(position,technical==null?java.util.Map.of():technical.attributes());}
    @com.fasterxml.jackson.annotation.JsonProperty("positionsEstimated")
    public boolean positionsEstimated(){return !position.equals("GOL");}
    public CatalogPlayerDto withPhotoUrl(String value) {
        return new CatalogPlayerDto(
            id,
            teamId,
            name,
            position,
            level,
            ability,
            attribute1,
            value1,
            attribute2,
            value2,
            age,
            number,
            value,
            nationality,
            appearances,
            goals,
            assists,
            minutes,
            sourceUrl,
            generated,
            generationOrigin,
            technical
        );
    }
}

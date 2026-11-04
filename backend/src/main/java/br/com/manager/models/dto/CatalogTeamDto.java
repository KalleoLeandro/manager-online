package br.com.manager.models.dto;

public record CatalogTeamDto(
    Long id,
    Long leagueId,
    String name,
    String shortName,
    String abbreviation,
    String crestUrl,
    String color,
    int rating,
    int nationalRank,
    double internationalBonus,
    boolean selectable,
    String sourceUrl,
    String groupName
) {
    public CatalogTeamDto withCrestUrl(String value) {
        return new CatalogTeamDto(
            id,
            leagueId,
            name,
            shortName,
            abbreviation,
            value,
            color,
            rating,
            nationalRank,
            internationalBonus,
            selectable,
            sourceUrl,
            groupName
        );
    }
}

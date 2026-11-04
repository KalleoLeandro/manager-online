package br.com.manager.models.dto;

public record CatalogLeagueDto(
    Long id,
    String name,
    String country,
    boolean selectable,
    String season,
    int teamCount,
    int division,
    String sourceUrl
) {}

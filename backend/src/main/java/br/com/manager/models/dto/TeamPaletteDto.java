package br.com.manager.models.dto;

import java.util.List;

public record TeamPaletteDto(
    String primary,
    String secondary,
    boolean awayVerified,
    String awayAccent,
    String awaySource,
    String homeAccent,
    List<String> homeColors,
    String homeSource,
    String homeMethod
) {}

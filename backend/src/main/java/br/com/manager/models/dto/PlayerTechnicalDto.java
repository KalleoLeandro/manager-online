package br.com.manager.models.dto;

import java.util.List;
import java.util.Map;

public record PlayerTechnicalDto(
    Map<String, Integer> attributes,
    int potential,
    List<String> sourcedAttributes,
    String sourceName,
    String sourceUrl
) {}

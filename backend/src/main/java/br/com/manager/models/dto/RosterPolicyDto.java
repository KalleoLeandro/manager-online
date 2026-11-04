package br.com.manager.models.dto;

import java.util.List;

public record RosterPolicyDto(
    String country,
    String season,
    List<Long> leagueIds,
    String reference,
    String windowStart,
    String windowEnd,
    String sourceUrl,
    String verification,
    String note
) {}

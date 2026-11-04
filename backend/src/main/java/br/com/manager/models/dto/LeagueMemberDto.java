package br.com.manager.models.dto;

public record LeagueMemberDto(
    String id,
    String name,
    Long teamId,
    String teamName,
    String crestUrl,
    boolean owner,
    boolean ready
) {}

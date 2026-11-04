package br.com.manager.models.responses;

public record SessionResponse(
    String id,
    String name,
    String accessToken,
    String photoUrl,
    String firstName,
    String lastName
) {}

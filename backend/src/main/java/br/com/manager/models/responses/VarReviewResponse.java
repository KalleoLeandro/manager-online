package br.com.manager.models.responses;

public record VarReviewResponse(
    int goalSequence,
    long teamId,
    String phase,
    long remainingMillis,
    Boolean confirmed,
    String reason
) {}

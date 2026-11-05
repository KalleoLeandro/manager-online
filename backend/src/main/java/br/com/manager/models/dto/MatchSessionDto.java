package br.com.manager.models.dto;

import java.time.Instant;

public record MatchSessionDto(MatchStateDto state, Instant nextTick, boolean finished) {}

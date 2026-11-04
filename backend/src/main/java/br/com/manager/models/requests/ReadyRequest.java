package br.com.manager.models.requests;

import jakarta.validation.constraints.NotNull;

public record ReadyRequest(@NotNull Boolean ready) {}

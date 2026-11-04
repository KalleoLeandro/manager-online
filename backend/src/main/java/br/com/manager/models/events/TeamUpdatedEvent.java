package br.com.manager.models.events;

public record TeamUpdatedEvent(String saveId, Long teamId, int version) {}

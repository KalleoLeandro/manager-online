package br.com.manager.models.events;

public record MatchUpdatedEvent(String saveId, long fixtureId, long revision) {}

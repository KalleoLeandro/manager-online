package br.com.manager.models.events;
public record TournamentDrawCreatedEvent(String saveId,long editionId,int stage) {}

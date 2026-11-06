package br.com.manager.models.events;

import java.util.Set;

public record SessionRevokedEvent(Set<String> tokenHashes) {}

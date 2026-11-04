package br.com.manager.service;

import br.com.manager.models.events.LeagueDeletedEvent;
import br.com.manager.repository.LeagueLifecycleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueLifecycleService {

    public static final int INACTIVE_DAYS = 60;

    private final LeagueLifecycleRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public LeagueLifecycleService(LeagueLifecycleRepository repository, Clock clock, ApplicationEventPublisher events) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
    }

    public Instant now() {
        return clock.instant();
    }

    public Instant expiresAt(Instant lastActivity) {
        return lastActivity.plus(Duration.ofDays(INACTIVE_DAYS));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void touch(String id) {
        repository.touch(id, now());
    }

    public List<String> expiredCandidates() {
        return repository.expiredBefore(now().minus(Duration.ofDays(INACTIVE_DAYS)));
    }

    @Transactional
    public boolean expire(String id) {
        var rows = repository.lockActivity(id);
        if (rows.isEmpty() || !rows.orElseThrow().isBefore(now().minus(Duration.ofDays(INACTIVE_DAYS)))) return false;
        removeLocked(id, "INACTIVE");
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeLocked(String id, String reason) {
        repository.delete(id);
        events.publishEvent(new LeagueDeletedEvent(id, reason));
    }
}

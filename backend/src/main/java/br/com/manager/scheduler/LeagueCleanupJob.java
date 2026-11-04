package br.com.manager.scheduler;

import br.com.manager.service.LeagueLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.leagues.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class LeagueCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LeagueCleanupJob.class);
    private final LeagueLifecycleService lifecycle;

    public LeagueCleanupJob(LeagueLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(
        fixedDelayString = "${app.leagues.cleanup.delay-ms:3600000}",
        initialDelayString = "${app.leagues.cleanup.initial-delay-ms:60000}"
    )
    public void removeInactive() {
        for (var id : lifecycle.expiredCandidates())
            try {
                lifecycle.expire(id);
            } catch (Exception error) {
                log.error("Falha ao limpar a liga inativa {}", id, error);
            }
    }
}

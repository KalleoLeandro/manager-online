package br.com.manager.scheduler;

import br.com.manager.service.MatchdayService;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.matchday.enabled", havingValue = "true", matchIfMissing = true)
public class MatchdayJob {

    private static final Logger log = LoggerFactory.getLogger(MatchdayJob.class);
    private final MatchdayService service;

    public MatchdayJob(MatchdayService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 250)
    public void advance() {
        for (var id : service.candidates())
            try {
                service.tick(id);
            } catch (Exception error) {
                log.error("Falha ao avançar a jornada da liga {}", id, error);
            }
    }
}

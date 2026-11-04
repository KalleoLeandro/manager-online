package br.com.manager.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LeagueLifecycleConfig {

    @Bean
    public Clock leagueClock() {
        return Clock.systemUTC();
    }
}

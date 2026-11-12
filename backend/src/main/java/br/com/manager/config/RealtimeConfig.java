package br.com.manager.config;

import br.com.manager.websocket.SaveSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@EnableScheduling
public class RealtimeConfig implements WebSocketConfigurer {

    private final SaveSocketHandler handler;
    private final String[] origins;

    public RealtimeConfig(SaveSocketHandler handler, @Value("${app.websocket.allowed-origins:}") String[] origins) {
        this.handler = handler;
        this.origins = java.util.Arrays.stream(origins)
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/saves").setAllowedOrigins(origins);
    }
}

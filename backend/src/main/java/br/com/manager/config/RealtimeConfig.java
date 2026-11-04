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

    public RealtimeConfig(
        SaveSocketHandler handler,
        @Value("${app.websocket.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String[] origins
    ) {
        this.handler = handler;
        this.origins = origins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/saves").setAllowedOrigins(origins);
    }
}

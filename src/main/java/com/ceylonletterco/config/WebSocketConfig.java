package com.ceylonletterco.config;

import com.ceylonletterco.websocket.NotificationEndpoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationEndpoint notificationEndpoint;

    public WebSocketConfig(NotificationEndpoint notificationEndpoint) {
        this.notificationEndpoint = notificationEndpoint;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationEndpoint, "/ws/notifications")
                .setAllowedOrigins("*");
    }
}

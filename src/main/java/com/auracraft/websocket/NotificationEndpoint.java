package com.auracraft.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Component
public class NotificationEndpoint extends TextWebSocketHandler {

    private static final Logger LOG = Logger.getLogger(NotificationEndpoint.class.getName());

    // Map userId -> WebSocketSession
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().toString();
        String query = session.getUri().getQuery();
        String userId = "unknown";
        if (query != null && query.contains("userId=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("userId=")) {
                    userId = param.split("=")[1];
                    break;
                }
            }
        }
        sessions.put(userId, session);
        LOG.fine("WebSocket connected for userId: " + userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.values().removeIf(s -> s.getId().equals(session.getId()));
        LOG.fine("WebSocket disconnected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    public static void sendNotification(String targetUserId, String jsonMessage) {
        WebSocketSession session = sessions.get(targetUserId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(jsonMessage));
                LOG.fine("Sent notification to userId=" + targetUserId);
            } catch (Exception e) {
                LOG.severe("Failed to send notification: " + e.getMessage());
            }
        } else {
            LOG.fine("No active WebSocket session for userId=" + targetUserId);
        }
    }

    public static void broadcastToAll(String jsonMessage) {
        TextMessage textMessage = new TextMessage(jsonMessage);
        for (WebSocketSession session : sessions.values()) {
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (Exception ignored) {}
            }
        }
    }
}

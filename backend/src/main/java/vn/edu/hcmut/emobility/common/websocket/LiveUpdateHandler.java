package vn.edu.hcmut.emobility.common.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** WebSocket endpoint /ws: pushes live updates (charging progress, notifications, hub status). */
@Component
public class LiveUpdateHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateHandler.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) { sessions.remove(s); continue; }
            try {
                synchronized (s) { s.sendMessage(message); }
            } catch (IOException e) {
                log.debug("WebSocket send failed: {}", e.getMessage());
                sessions.remove(s);
            }
        }
    }

    public int connectedClients() { return sessions.size(); }
}

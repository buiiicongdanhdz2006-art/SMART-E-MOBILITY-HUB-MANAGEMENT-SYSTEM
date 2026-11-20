package vn.edu.hcmut.emobility.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Publishes {"type": ..., "time": ..., ...payload} to every WebSocket client. */
@Component
public class LiveUpdatePublisher {

    private final LiveUpdateHandler handler;
    private final ObjectMapper mapper;

    public LiveUpdatePublisher(LiveUpdateHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    public void publish(String type, Map<String, Object> payload) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type);
            m.put("time", LocalDateTime.now().toString());
            m.putAll(payload);
            handler.broadcast(mapper.writeValueAsString(m));
        } catch (Exception ignored) {
            // live updates are best-effort; the REST API stays the source of truth
        }
    }
}

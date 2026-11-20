package vn.edu.hcmut.emobility.iot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MQTT subscriber of F-12: subscribes to emobility/# on the Mosquitto broker and forwards every message to
 * IoTDataService. If the broker is not reachable the backend keeps running and retries in the background.
 */
@Component
public class MqttSubscriber implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);
    private static final String TOPIC = "emobility/#";

    private final IoTDataService ioTDataService;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String brokerUrl;
    private final String clientId;
    private volatile MqttClient client;
    private volatile boolean stopping;

    public MqttSubscriber(IoTDataService ioTDataService, ObjectMapper mapper,
                          @Value("${iot.mqtt.enabled:true}") boolean enabled,
                          @Value("${iot.mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
                          @Value("${iot.mqtt.client-id:emobility-backend}") String clientId) {
        this.ioTDataService = ioTDataService;
        this.mapper = mapper;
        this.enabled = enabled;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("MQTT disabled (iot.mqtt.enabled=false); use POST /api/iot/events instead");
            return;
        }
        Thread t = new Thread(this::connectLoop, "mqtt-connector");
        t.setDaemon(true);
        t.start();
    }

    private void connectLoop() {
        long delay = 2000;
        while (!stopping) {
            try {
                MqttClient c = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);
                c.setCallback(this);
                c.connect(options);
                c.subscribe(TOPIC, 1);
                client = c;
                log.info("MQTT connected to {} and subscribed to {}", brokerUrl, TOPIC);
                return;
            } catch (MqttException e) {
                log.warn("MQTT broker {} not reachable ({}), retry in {} s", brokerUrl, e.getMessage(), delay / 1000);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delay = Math.min(delay * 2, 30000);
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect && client != null) {
            try {
                client.subscribe(TOPIC, 1);
                log.info("MQTT reconnected to {}", serverURI);
            } catch (MqttException e) {
                log.warn("MQTT resubscribe failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String body = new String(message.getPayload(), StandardCharsets.UTF_8);
            JsonNode payload = body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
            ioTDataService.handle(topic, payload);
        } catch (Exception e) {
            log.warn("Bad MQTT message on {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // subscriber only
    }

    @PreDestroy
    public void stop() {
        stopping = true;
        try {
            if (client != null && client.isConnected()) client.disconnect();
        } catch (MqttException ignored) {
            // shutting down
        }
    }

    public boolean isConnected() { return client != null && client.isConnected(); }
}

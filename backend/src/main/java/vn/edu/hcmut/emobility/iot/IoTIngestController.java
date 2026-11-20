package vn.edu.hcmut.emobility.iot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.common.exception.ApiException;

/**
 * HTTP entry for IoT data (same messages as MQTT), used by the simulator in "--transport http" mode
 * and by tests. Protected by the X-IoT-Key header.
 */
@RestController
@RequestMapping("/api/iot")
public class IoTIngestController {

    private final IoTDataService ioTDataService;
    private final ObjectMapper mapper;
    private final String apiKey;

    public IoTIngestController(IoTDataService ioTDataService, ObjectMapper mapper, @Value("${iot.api-key:emobility-iot-key}") String apiKey) {
        this.ioTDataService = ioTDataService;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    /** Body: {"topic": "emobility/ports/H01-P01/battery", "payload": {...}}. */
    @PostMapping("/events")
    public Map<String, Object> ingest(@RequestHeader(value = "X-IoT-Key", required = false) String key, @RequestBody JsonNode body) {
        check(key);
        String topic = body.path("topic").asText(null);
        if (topic == null) throw ApiException.badRequest("Thiếu topic");
        JsonNode payload = body.has("payload") ? body.get("payload") : mapper.createObjectNode();
        ioTDataService.handle(topic, payload);
        return Map.of("accepted", true);
    }

    @GetMapping("/devices")
    public Map<String, Object> devices(@RequestHeader(value = "X-IoT-Key", required = false) String key) {
        check(key);
        return ioTDataService.devices();
    }

    private void check(String key) {
        if (!apiKey.equals(key)) throw ApiException.unauthorized("Sai X-IoT-Key");
    }
}

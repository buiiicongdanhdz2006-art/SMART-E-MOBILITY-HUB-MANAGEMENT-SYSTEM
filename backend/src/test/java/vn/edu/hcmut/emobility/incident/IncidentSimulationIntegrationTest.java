package vn.edu.hcmut.emobility.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.WaitingQueueRepository;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.security.PasswordHasher;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/**
 * End-to-end tests (real Spring context, H2, Tomcat, REST + session token) for the two other modules
 * that carry the test cases in the component diagram: F-09 Incident &amp; Maintenance and F-10 What-if
 * Simulation. Also checks the role / permission rules on these APIs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IncidentSimulationIntegrationTest {

    private static final String PASSWORD = "Test@1234";

    @Autowired HubRepository hubs;
    @Autowired WaitingQueueRepository queues;
    @Autowired ChargingPortRepository ports;
    @Autowired VehicleRepository vehicles;
    @Autowired AccountRepository accounts;
    @Autowired TestRestTemplate http;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void incidentLifecycleThroughRestApi() throws Exception {
        Hub hub = hubs.save(new Hub("T03", "Hub sự cố", "addr", 10.87, 106.80, 4));
        queues.save(new WaitingQueue(hub));
        Vehicle v = vehicles.save(new Vehicle("T03-V01", VehicleType.E_MOTORBIKE, "VinFast Evo", hub, 80));
        accounts.save(new Operator("it-incident@emobility.vn", "0933000001", "IT Incident", PasswordHasher.hash(PASSWORD), "MANAGE_INCIDENT"));
        accounts.save(new Operator("it-fleet@emobility.vn", "0933000002", "IT Fleet", PasswordHasher.hash(PASSWORD), "VIEW_FLEET"));
        accounts.save(new User("it3@hcmut.edu.vn", "0911111113", "2419003", "IT 3", PasswordHasher.hash(PASSWORD)));
        String op = login("it-incident@emobility.vn");
        String fleetOp = login("it-fleet@emobility.vn");
        String user = login("it3@hcmut.edu.vn");

        // Security: not signed in -> 401, wrong role or missing permission -> 403
        assertEquals(401, call(HttpMethod.GET, "/api/incidents", null, null).getStatusCode().value());
        assertEquals(403, call(HttpMethod.GET, "/api/incidents", user, null).getStatusCode().value());
        assertEquals(403, call(HttpMethod.GET, "/api/incidents", fleetOp, null).getStatusCode().value());

        // UC-F09-01: incomplete report -> 400; complete report -> REPORTED, Hub taken from the vehicle
        assertEquals(400, call(HttpMethod.POST, "/api/incidents", user,
                Map.of("type", "VEHICLE_FAULT", "vehicleId", v.getId())).getStatusCode().value());
        ResponseEntity<String> created = call(HttpMethod.POST, "/api/incidents", user, Map.of(
                "type", "VEHICLE_FAULT", "severity", "HIGH", "vehicleId", v.getId(),
                "title", "Phanh yếu", "description", "Xe T03-V01 phanh không ăn"));
        assertEquals(200, created.getStatusCode().value(), created.getBody());
        JsonNode incident = json.readTree(created.getBody());
        long id = incident.get("id").asLong();
        assertEquals("REPORTED", incident.get("status").asText());
        assertEquals(hub.getId().longValue(), incident.get("hubId").asLong());

        // Wrong order (resolve before investigation) -> 409
        assertEquals(409, call(HttpMethod.POST, "/api/incidents/" + id + "/resolve", op,
                Map.of("note", "chưa kiểm tra")).getStatusCode().value());

        // UC-F09-02 investigate, UC-F09-03 assign to maintenance -> vehicle MAINTENANCE
        assertEquals("INVESTIGATING", statusOf(call(HttpMethod.POST, "/api/incidents/" + id + "/investigate", op, null)));
        assertEquals("MAINTENANCE", statusOf(call(HttpMethod.POST, "/api/incidents/" + id + "/maintenance", op, null)));
        assertEquals(VehicleStatus.MAINTENANCE, vehicles.findById(v.getId()).orElseThrow().getStatus());

        // UC-F09-04 resolve -> vehicle back to AVAILABLE
        assertEquals("RESOLVED", statusOf(call(HttpMethod.POST, "/api/incidents/" + id + "/resolve", op,
                Map.of("note", "Đã thay má phanh"))));
        assertEquals(VehicleStatus.AVAILABLE, vehicles.findById(v.getId()).orElseThrow().getStatus());

        // UC-F09-05: history filtered by status + vehicle, detail keeps every status change
        ResponseEntity<String> history = call(HttpMethod.GET, "/api/incidents?status=RESOLVED&vehicleId=" + v.getId(), op, null);
        assertEquals(200, history.getStatusCode().value(), history.getBody());
        JsonNode list = json.readTree(history.getBody());
        assertEquals(1, list.size());
        assertEquals(id, list.get(0).get("id").asLong());
        JsonNode detail = json.readTree(call(HttpMethod.GET, "/api/incidents/" + id, op, null).getBody());
        assertEquals(4, detail.get("statusHistory").size());
    }

    @Test
    void whatIfSimulationThroughRestApi() throws Exception {
        Hub hub = hubs.save(new Hub("T04", "Hub mô phỏng", "addr", 10.875, 106.80, 6));
        queues.save(new WaitingQueue(hub));
        ports.save(new ChargingPort("T04-P01", hub, "A", 7.0));
        ports.save(new ChargingPort("T04-P02", hub, "A", 7.0));
        vehicles.save(new Vehicle("T04-V01", VehicleType.E_BIKE, "e-bike", hub, 90));
        accounts.save(new Operator("it-sim@emobility.vn", "0933000003", "IT Simulation", PasswordHasher.hash(PASSWORD), "RUN_SIMULATION"));
        String op = login("it-sim@emobility.vn");

        assertEquals(401, call(HttpMethod.GET, "/api/simulation/scenarios", null, null).getStatusCode().value());

        // F10-AF-01: parameter out of range -> 400, scenario not stored
        ResponseEntity<String> bad = call(HttpMethod.POST, "/api/simulation/scenarios", op,
                Map.of("type", "CONGESTION_SETTING", "hubId", hub.getId(), "demandMultiplier", 9));
        assertEquals(400, bad.getStatusCode().value(), bad.getBody());

        // UC-F10-01..04: one of the two ports fails -> impact metrics + suggested actions (F-09 repair first)
        ResponseEntity<String> ok = call(HttpMethod.POST, "/api/simulation/scenarios", op,
                Map.of("type", "HARDWARE_FAILURE", "hubId", hub.getId(), "failedPorts", 1));
        assertEquals(200, ok.getStatusCode().value(), ok.getBody());
        JsonNode result = json.readTree(ok.getBody());
        assertTrue(result.get("successful").asBoolean(), ok.getBody());
        assertEquals(2, result.get("before").get("workingPorts").asInt());
        assertEquals(1, result.get("after").get("workingPorts").asInt());
        assertTrue(result.get("suggestions").size() > 0);
        assertTrue(ok.getBody().contains("F-09"), ok.getBody());

        // F10-AF-03: Rerun returns a copy of the last valid scenario to prefill the form
        ResponseEntity<String> rerun = call(HttpMethod.GET, "/api/simulation/rerun", op, null);
        assertEquals(200, rerun.getStatusCode().value(), rerun.getBody());
        JsonNode previous = json.readTree(rerun.getBody());
        assertEquals("HARDWARE_FAILURE", previous.get("type").asText());
        assertEquals("1", previous.get("parameters").get("failedPorts").asText());
    }

    private String login(String id) throws Exception {
        ResponseEntity<String> r = http.postForEntity("/api/auth/login", Map.of("id", id, "password", PASSWORD), String.class);
        assertEquals(200, r.getStatusCode().value(), r.getBody());
        return json.readTree(r.getBody()).get("token").asText();
    }

    private ResponseEntity<String> call(HttpMethod method, String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private String statusOf(ResponseEntity<String> response) throws Exception {
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        return json.readTree(response.getBody()).get("status").asText();
    }
}

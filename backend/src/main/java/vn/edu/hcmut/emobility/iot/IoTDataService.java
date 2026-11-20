package vn.edu.hcmut.emobility.iot;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.charging.controller.IoTPortMonitor;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.ChargingRequestRepository;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.common.enums.ReservationStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.fleet.entity.DispatchOrder;
import vn.edu.hcmut.emobility.fleet.repository.DispatchOrderRepository;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.parking.repository.ParkingReservationRepository;
import vn.edu.hcmut.emobility.parking.repository.ParkingSlotRepository;
import vn.edu.hcmut.emobility.parking.service.ParkingService;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/**
 * F-12 IoT / simulation data processing: receives sensor data (from MQTT or HTTP) and updates the
 * vehicle, parking slot and charging port states. Topics:
 * <pre>
 * emobility/ports/{portCode}/plugged      {"vehicleId": "V004"}
 * emobility/ports/{portCode}/battery      {"soc": 46.2, "voltage": 382.5, "current": 10.0, "temperature": 27.3}
 * emobility/ports/{portCode}/status       {"status": "FIXING" | "AVAILABLE"}
 * emobility/ports/{portCode}/lost         {}
 * emobility/vehicles/{vehicleCode}/telemetry {"battery": 81, "lat": 10.87, "lng": 106.80, "speed": 12.4, "tirePressure": 238.6, "event": "Normal"}
 * emobility/slots/{slotCode}/status       {"occupied": true}
 * </pre>
 */
@Service
public class IoTDataService {

    private static final Logger log = LoggerFactory.getLogger(IoTDataService.class);
    private final IoTPortMonitor portMonitor;
    private final ParkingService parkingService;
    private final DispatchOrderRepository dispatchOrders;
    private final HubRepository hubs;
    private final ChargingPortRepository ports;
    private final ChargingRequestRepository chargingRequests;
    private final VehicleRepository vehicles;
    private final ParkingSlotRepository slots;
    private final ParkingReservationRepository reservations;
    private final VehicleTelemetryHandler telemetryHandler;

    public IoTDataService(IoTPortMonitor portMonitor, ParkingService parkingService, DispatchOrderRepository dispatchOrders, HubRepository hubs, ChargingPortRepository ports, ChargingRequestRepository chargingRequests, VehicleRepository vehicles, ParkingSlotRepository slots, ParkingReservationRepository reservations, VehicleTelemetryHandler telemetryHandler) {
        this.portMonitor = portMonitor;
        this.parkingService = parkingService;
        this.dispatchOrders = dispatchOrders;
        this.hubs = hubs;
        this.ports = ports;
        this.chargingRequests = chargingRequests;
        this.vehicles = vehicles;
        this.slots = slots;
        this.reservations = reservations;
        this.telemetryHandler = telemetryHandler;
    }

    /** Routes one message. Errors are logged so that one bad message never stops the stream. */
    public void handle(String topic, JsonNode payload) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 4 || !"emobility".equals(parts[0])) {
                log.debug("Ignored topic {}", topic);
                return;
            }
            String kind = parts[1];
            String code = parts[2];
            String event = parts[3];
            switch (kind) {
                case "ports" -> handlePort(code, event, payload);
                case "vehicles" -> telemetryHandler.handle(code, payload);
                case "slots" -> parkingService.onSlotSensor(code, payload.path("occupied").asBoolean(false));
                default -> log.debug("Unknown device kind {}", kind);
            }
        } catch (Exception e) {
            log.warn("IoT message {} rejected: {}", topic, e.getMessage());
        }
    }

    private void handlePort(String portCode, String event, JsonNode p) {
        switch (event) {
            case "plugged" -> portMonitor.onVehiclePlugged(portCode, text(p, "vehicleId"));
            case "battery" -> portMonitor.onBatteryReading(portCode, (int) Math.round(p.path("soc").asDouble()),
                    num(p, "voltage"), num(p, "current"), num(p, "temperature"));
            case "status" -> portMonitor.onPortStatus(portCode, PortStatus.valueOf(p.path("status").asText()));
            case "lost" -> portMonitor.onConnectionLost(portCode);
            default -> log.debug("Unknown port event {}", event);
        }
    }

    /** Device list for the simulator: what exists and what it should simulate now. */
    @Transactional(readOnly = true)
    public Map<String, Object> devices() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> hubList = new ArrayList<>();
        for (Hub h : hubs.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("code", h.getCode());
            m.put("lat", h.getLatitude());
            m.put("lng", h.getLongitude());
            hubList.add(m);
        }
        out.put("hubs", hubList);

        List<Map<String, Object>> portList = new ArrayList<>();
        for (ChargingPort p : ports.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getCode());
            m.put("hubId", p.getHubId());
            m.put("status", p.getStatus().name());
            ChargingRequest r = chargingRequests.findActiveByPort(p.getId()).orElse(null);
            if (r != null) {
                m.put("requestStatus", r.getStatus().name());
                m.put("vehicleRef", r.getVehicleRef());
                m.put("battery", r.getSession() != null && r.getSession().getCurrentBattery() != null ? r.getSession().getCurrentBattery() : r.getBatteryLevel());
                m.put("targetBattery", r.getTargetBattery());
            }
            portList.add(m);
        }
        out.put("ports", portList);

        List<Map<String, Object>> vehicleList = new ArrayList<>();
        for (Vehicle v : vehicles.findAllByOrderByCode()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", v.getCode());
            m.put("type", v.getType().name());
            m.put("status", v.getStatus().name());
            m.put("battery", v.getBatteryLevel());
            m.put("lat", v.getLatitude());
            m.put("lng", v.getLongitude());
            m.put("hubId", v.getHubId());
            if (v.getStatus() == VehicleStatus.IN_TRANSIT) {
                for (DispatchOrder o : dispatchOrders.findActive()) {
                    if (o.getVehicles().stream().anyMatch(x -> x.getId().equals(v.getId()))) {
                        m.put("destLat", o.getToHub().getLatitude());
                        m.put("destLng", o.getToHub().getLongitude());
                    }
                }
            }
            vehicleList.add(m);
        }
        out.put("vehicles", vehicleList);

        List<Map<String, Object>> slotList = new ArrayList<>();
        for (ParkingSlot s : slots.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getCode());
            m.put("status", s.getStatus().name());
            m.put("checkedIn", !reservations.findBySlotIdAndStatusIn(s.getId(), List.of(ReservationStatus.CHECKED_IN)).isEmpty());
            slotList.add(m);
        }
        out.put("slots", slotList);
        out.put("chargingRequests", chargingRequests.findByStatus(RequestStatus.CHARGING).size());
        return out;
    }

    private static Double num(JsonNode p, String field) {
        JsonNode n = p.get(field);
        return n == null || n.isNull() ? null : n.asDouble();
    }

    private static String text(JsonNode p, String field) {
        JsonNode n = p.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}

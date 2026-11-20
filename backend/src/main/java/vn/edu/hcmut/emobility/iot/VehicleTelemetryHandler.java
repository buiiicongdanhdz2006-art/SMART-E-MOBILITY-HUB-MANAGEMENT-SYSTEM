package vn.edu.hcmut.emobility.iot;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.booking.repository.BookingRepository;
import vn.edu.hcmut.emobility.common.api.IIncidentReport;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.api.IVehicleState;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.BookingStatus;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.websocket.LiveUpdatePublisher;
import vn.edu.hcmut.emobility.fleet.entity.DispatchOrder;
import vn.edu.hcmut.emobility.fleet.repository.DispatchOrderRepository;
import vn.edu.hcmut.emobility.fleet.service.FleetDispatchService;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/**
 * Vehicle telemetry of F-12 (one transaction per message): battery, position, tyre pressure and driving
 * events. Sends low-battery notifications (F-13), auto-detects incidents (F-09) and detects dispatch arrivals (F-08).
 */
@Component
public class VehicleTelemetryHandler {

    private static final Logger log = LoggerFactory.getLogger(VehicleTelemetryHandler.class);
    public static final int LOW_BATTERY = 20;

    private final IVehicleState vehicleState;
    private final IIncidentReport incidentReport;
    private final INotification notification;
    private final BookingRepository bookings;
    private final DispatchOrderRepository dispatchOrders;
    private final FleetDispatchService fleetService;
    private final LiveUpdatePublisher live;
    private final double minTirePressure;

    public VehicleTelemetryHandler(IVehicleState vehicleState, IIncidentReport incidentReport, INotification notification,
                                   BookingRepository bookings, DispatchOrderRepository dispatchOrders,
                                   FleetDispatchService fleetService, LiveUpdatePublisher live,
                                   @Value("${iot.min-tire-pressure-kpa:180}") double minTirePressure) {
        this.vehicleState = vehicleState;
        this.incidentReport = incidentReport;
        this.notification = notification;
        this.bookings = bookings;
        this.dispatchOrders = dispatchOrders;
        this.fleetService = fleetService;
        this.live = live;
        this.minTirePressure = minTirePressure;
    }

    @Transactional
    public void handle(String vehicleCode, JsonNode p) {
        Vehicle v = vehicleState.findByCode(vehicleCode);
        if (v == null) {
            log.debug("Unknown vehicle {}", vehicleCode);
            return;
        }
        int before = v.getBatteryLevel();
        Integer battery = p.has("battery") ? (int) Math.round(p.path("battery").asDouble()) : null;
        Double lat = num(p, "lat");
        Double lng = num(p, "lng");
        // A vehicle parked at a Hub keeps the Hub position; only moving vehicles report their location.
        boolean moving = v.getStatus() == VehicleStatus.IN_USE || v.getStatus() == VehicleStatus.IN_TRANSIT;
        v = vehicleState.updateTelemetry(v.getId(), v.getStatus() == VehicleStatus.CHARGING ? null : battery,
                moving ? lat : null, moving ? lng : null);
        Double tire = num(p, "tirePressure");
        if (tire != null) v.setTirePressureKpa(tire);

        if (battery != null && before >= LOW_BATTERY && battery < LOW_BATTERY && v.getStatus() != VehicleStatus.CHARGING) {
            notification.notifyOperators(AlertType.LOW_BATTERY, "Xe pin thấp",
                    "Xe " + v.getCode() + " còn " + battery + "% pin" + (v.getHubName() != null ? " tại " + v.getHubName() : "") + ".");
            if (v.getStatus() == VehicleStatus.IN_USE) {
                for (Booking b : bookings.findByStatus(BookingStatus.IN_PROGRESS)) {
                    if (b.getVehicleId().equals(v.getId())) {
                        notification.notifyUser(b.getUserId(), AlertType.LOW_BATTERY, "Pin xe sắp hết",
                                "Xe " + v.getCode() + " còn " + battery + "% pin, hãy trả xe tại trạm gần nhất.");
                    }
                }
            }
        }
        if (tire != null && tire < minTirePressure && !incidentReport.hasOpenIncident(IncidentType.VEHICLE_FAULT, v.getId(), null)) {
            incidentReport.reportIncident(IncidentType.VEHICLE_FAULT, SeverityLevel.MEDIUM, v.getHubId(), v.getId(), null,
                    "Áp suất lốp thấp - xe " + v.getCode(), "Cảm biến báo áp suất lốp " + tire + " kPa (ngưỡng " + minTirePressure + " kPa).", null);
        }
        String ev = text(p, "event");
        if (ev != null && ev.toLowerCase().contains("collision") && !incidentReport.hasOpenIncident(IncidentType.ACCIDENT, v.getId(), null)) {
            incidentReport.reportIncident(IncidentType.ACCIDENT, SeverityLevel.CRITICAL, v.getHubId(), v.getId(), null,
                    "Phát hiện va chạm - xe " + v.getCode(), "Cảm biến báo sự kiện " + ev + ".", null);
        }
        if (v.getStatus() == VehicleStatus.IN_TRANSIT) {
            Long vehicleIdFinal = v.getId();
            for (DispatchOrder o : dispatchOrders.findActive()) {
                if (o.getVehicles().stream().anyMatch(x -> x.getId().equals(vehicleIdFinal))) fleetService.refreshDispatchProgress(o.getId());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vehicleCode", v.getCode());
        m.put("battery", v.getBatteryLevel());
        m.put("status", v.getStatus().name());
        m.put("lat", v.getLatitude());
        m.put("lng", v.getLongitude());
        live.publish("vehicle", m);
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

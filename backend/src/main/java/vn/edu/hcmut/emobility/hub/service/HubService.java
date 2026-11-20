package vn.edu.hcmut.emobility.hub.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.enums.AlertStatus;
import vn.edu.hcmut.emobility.common.enums.LoadLevel;
import vn.edu.hcmut.emobility.common.enums.Need;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.SlotStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.hub.dto.AlertDetail;
import vn.edu.hcmut.emobility.hub.dto.HubStatus;
import vn.edu.hcmut.emobility.hub.dto.HubSummary;
import vn.edu.hcmut.emobility.hub.dto.Location;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.entity.OverloadAlert;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.hub.repository.OverloadAlertRepository;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** Business logic of HubController and AlertController (F-02); provides IHubQuery. */
@Service
public class HubService implements IHubQuery {

    public static final double WARNING_LEVEL = 0.80;
    public static final double OVERLOAD_LEVEL = 0.95;

    private final HubRepository hubs;
    private final OverloadAlertRepository alerts;

    public HubService(HubRepository hubs, OverloadAlertRepository alerts) {
        this.hubs = hubs;
        this.alerts = alerts;
    }

    // ---- IHubQuery -----------------------------------------------------------------------

    @Override
    public Hub getHub(Long hubId) {
        return hubs.findById(hubId).orElseThrow(() -> ApiException.notFound("trạm #" + hubId));
    }

    @Override
    public List<Hub> getAllHubs() { return hubs.findAll(); }

    @Override
    public boolean hasFreeParkingSlot(Long hubId, LocalDateTime start, LocalDateTime end) {
        return !getHub(hubId).getAvailableSlots(start, end).isEmpty();
    }

    // ---- UC-F02-01 .. 04 -----------------------------------------------------------------

    /** UC-F02-01: summary of every Hub. */
    @Transactional(readOnly = true)
    public List<HubSummary> getHubs() {
        return hubs.findAll().stream().sorted(Comparator.comparing(Hub::getCode)).map(this::summary).toList();
    }

    /** UC-F02-02: aggregates slots, ports and vehicles and sets the load level. */
    @Transactional(readOnly = true)
    public HubStatus getHubStatus(Long hubId) {
        return status(getHub(hubId));
    }

    /** UC-F02-03: the 3 nearest ACTIVE Hubs that still meet the need (empty list if none). */
    @Transactional(readOnly = true)
    public List<HubSummary> findNearestHubs(Location location, Need need) {
        return hubs.findAll().stream()
                .filter(Hub::isActive)
                .filter(h -> meetsNeed(h, need))
                .map(h -> summary(h).withDistance(distance(location, new Location(h.getLatitude(), h.getLongitude()))))
                .sorted(Comparator.comparing(HubSummary::distanceKm))
                .limit(3)
                .toList();
    }

    public List<OverloadAlert> getOpenAlerts() {
        return alerts.findByStatusOrderByCreatedAtDesc(AlertStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public AlertDetail getAlertDetail(Long alertId) {
        OverloadAlert a = alerts.findById(alertId).orElseThrow(() -> ApiException.notFound("cảnh báo"));
        return new AlertDetail(a, status(a.getHub()));
    }

    @Transactional
    public OverloadAlert resolveAlert(Long alertId, Long operatorId, String note) {
        OverloadAlert a = alerts.findById(alertId).orElseThrow(() -> ApiException.notFound("cảnh báo"));
        a.resolve(operatorId, LocalDateTime.now(), note);
        return alerts.save(a);
    }

    // ---- helpers -------------------------------------------------------------------------

    public HubSummary summary(Hub h) {
        return new HubSummary(h.getId(), h.getCode(), h.getName(), h.getAddress(), h.getLatitude(), h.getLongitude(),
                h.getState(), h.countFreeSlots(), h.countFreePorts(), h.countAvailable(), determineLoadLevel(h.utilisation()), null);
    }

    public HubStatus status(Hub h) {
        List<ParkingSlot> slots = h.getParkingSlots();
        List<ChargingPort> ports = h.getChargingPorts();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (VehicleStatus s : VehicleStatus.values()) byStatus.put(s.name(), 0);
        for (Vehicle v : h.getVehicles(null)) byStatus.merge(v.getStatus().name(), 1, Integer::sum);
        double util = h.utilisation();
        return new HubStatus(h.getId(), h.getName(), h.getState(),
                slots.size(),
                count(slots, SlotStatus.AVAILABLE), count(slots, SlotStatus.RESERVED), count(slots, SlotStatus.OCCUPIED),
                ports.size(), h.countFreePorts(),
                (int) ports.stream().filter(p -> p.getStatus() == PortStatus.CHARGING).count(),
                (int) ports.stream().filter(p -> p.getStatus() == PortStatus.FIXING).count(),
                h.waitingCount(), h.getVehicles(null).size(), byStatus, h.getExpectedDemand(),
                Math.round(util * 1000) / 1000.0, determineLoadLevel(util), h.getLastUpdated());
    }

    /** NORMAL, WARNING (utilisation &gt;= 80%) or OVERLOADED (utilisation &gt;= 95%). */
    public static LoadLevel determineLoadLevel(double utilisation) {
        if (utilisation >= OVERLOAD_LEVEL) return LoadLevel.OVERLOADED;
        if (utilisation >= WARNING_LEVEL) return LoadLevel.WARNING;
        return LoadLevel.NORMAL;
    }

    /** Straight-line (haversine) distance in km between two coordinates. */
    public static double distance(Location from, Location to) {
        double r = 6371.0;
        double dLat = Math.toRadians(to.latitude() - from.latitude());
        double dLng = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static boolean meetsNeed(Hub h, Need need) {
        if (need == null) return true;
        return switch (need) {
            case VEHICLE -> h.countAvailable() > 0;
            case PARKING -> h.countFreeSlots() > 0;
            case CHARGING -> h.countFreePorts() > 0;
        };
    }

    private static int count(List<ParkingSlot> slots, SlotStatus s) {
        return (int) slots.stream().filter(x -> x.getStatus() == s).count();
    }
}

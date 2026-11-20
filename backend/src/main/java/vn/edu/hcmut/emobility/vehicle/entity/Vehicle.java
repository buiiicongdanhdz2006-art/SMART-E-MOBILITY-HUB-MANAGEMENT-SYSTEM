package vn.edu.hcmut.emobility.vehicle.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** Shared electric vehicle (e-bike, e-motorbike, e-car) - shared by F-03, F-04, F-07, F-08. */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    /** Allowed transitions of the vehicle state chart. */
    private static final Map<VehicleStatus, Set<VehicleStatus>> TRANSITIONS = new EnumMap<>(VehicleStatus.class);

    static {
        TRANSITIONS.put(VehicleStatus.AVAILABLE, EnumSet.of(VehicleStatus.RESERVED, VehicleStatus.IN_TRANSIT,
                VehicleStatus.CHARGING, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE));
        TRANSITIONS.put(VehicleStatus.RESERVED, EnumSet.of(VehicleStatus.IN_USE, VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE));
        TRANSITIONS.put(VehicleStatus.IN_USE, EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE));
        TRANSITIONS.put(VehicleStatus.IN_TRANSIT, EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE));
        TRANSITIONS.put(VehicleStatus.CHARGING, EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE));
        TRANSITIONS.put(VehicleStatus.MAINTENANCE, EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.OUT_OF_SERVICE));
        TRANSITIONS.put(VehicleStatus.OUT_OF_SERVICE, EnumSet.of(VehicleStatus.MAINTENANCE, VehicleStatus.AVAILABLE));
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    private VehicleType type;

    private String model;

    @Enumerated(EnumType.STRING)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    private int batteryLevel = 100;
    private Double latitude;
    private Double longitude;

    @JsonIgnore
    @ManyToOne
    private Hub currentHub;

    /** Next time the vehicle is needed (booking pickup) - "near usage time" criterion of F-07. */
    private LocalDateTime nextUsageTime;

    private Double tirePressureKpa;
    private LocalDateTime lastTelemetryAt;
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Vehicle() {}

    public Vehicle(String code, VehicleType type, String model, Hub hub, int batteryLevel) {
        this.code = code;
        this.type = type;
        this.model = model;
        this.currentHub = hub;
        this.batteryLevel = batteryLevel;
        if (hub != null) {
            this.latitude = hub.getLatitude();
            this.longitude = hub.getLongitude();
        }
    }

    public static boolean canTransition(VehicleStatus from, VehicleStatus to) {
        return from == to || TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean isAvailable() { return status == VehicleStatus.AVAILABLE; }

    public boolean hasEnoughBattery(int min) { return batteryLevel >= min; }

    /** Changes the status if the transition is allowed, otherwise rejects it. */
    public void updateStatus(VehicleStatus newStatus) {
        if (!canTransition(status, newStatus)) {
            throw new IllegalStateException("Không thể chuyển xe " + code + " từ " + status + " sang " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /** Manual correction (UC-F08-05). */
    public void forceStatus(VehicleStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /** Sets the current Hub of the vehicle (arrival at the destination Hub / return). */
    public void moveToHub(Hub hub) {
        this.currentHub = hub;
        if (hub != null) {
            this.latitude = hub.getLatitude();
            this.longitude = hub.getLongitude();
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void leaveHub() { this.currentHub = null; }

    public void updateLocation(double lat, double lng) {
        this.latitude = lat;
        this.longitude = lng;
        this.lastTelemetryAt = LocalDateTime.now();
    }

    public void updateBattery(int level) {
        this.batteryLevel = Math.max(0, Math.min(100, level));
        this.lastTelemetryAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public VehicleStatus getStatus() { return status; }
    public int getBatteryLevel() { return batteryLevel; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    @JsonIgnore
    public Hub getCurrentHub() { return currentHub; }
    public Long getHubId() { return currentHub == null ? null : currentHub.getId(); }
    public String getHubName() { return currentHub == null ? null : currentHub.getName(); }
    public LocalDateTime getNextUsageTime() { return nextUsageTime; }
    public void setNextUsageTime(LocalDateTime nextUsageTime) { this.nextUsageTime = nextUsageTime; }
    public Double getTirePressureKpa() { return tirePressureKpa; }
    public void setTirePressureKpa(Double tirePressureKpa) { this.tirePressureKpa = tirePressureKpa; }
    public LocalDateTime getLastTelemetryAt() { return lastTelemetryAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

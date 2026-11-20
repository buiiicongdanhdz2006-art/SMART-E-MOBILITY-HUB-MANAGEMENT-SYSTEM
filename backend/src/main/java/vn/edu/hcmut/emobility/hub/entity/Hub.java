package vn.edu.hcmut.emobility.hub.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.common.enums.HubState;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.SlotStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/**
 * Mobility Hub (owned by F-02). Contains ParkingSlot, ChargingPort and WaitingQueue
 * (composition) and aggregates Vehicle.
 */
@Entity
@Table(name = "hubs")
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String address;
    private double latitude;
    private double longitude;

    @Enumerated(EnumType.STRING)
    private HubState state = HubState.ACTIVE;

    /** Expected number of vehicles needed at the Hub (used by F-08 imbalance detection, F-10). */
    private int expectedDemand;

    /** Number of waiting charging requests above which the Hub is overloaded (F-02.3 / F-07). */
    private int overloadThreshold = 5;

    /** Opening hours, used by the HOURS_SETTING what-if scenario (F-10). */
    private int openHour = 5;
    private int closeHour = 23;

    private LocalDateTime lastUpdated = LocalDateTime.now();

    @JsonIgnore
    @OneToMany(mappedBy = "hub")
    private List<ParkingSlot> parkingSlots = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "hub")
    private List<ChargingPort> chargingPorts = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "currentHub")
    private List<Vehicle> vehicles = new ArrayList<>();

    @JsonIgnore
    @OneToOne(mappedBy = "hub")
    private WaitingQueue waitingQueue;

    protected Hub() {}

    public Hub(String code, String name, String address, double latitude, double longitude, int expectedDemand) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.expectedDemand = expectedDemand;
    }

    // ---- F-02 -------------------------------------------------------------------------

    @JsonIgnore
    public List<ParkingSlot> getParkingSlots() { return parkingSlots; }

    @JsonIgnore
    public List<ChargingPort> getChargingPorts() { return chargingPorts; }

    /** Parking slots of the Hub that are free in the given period (used by F-05). */
    @JsonIgnore
    public List<ParkingSlot> getAvailableSlots(LocalDateTime start, LocalDateTime end) {
        return parkingSlots.stream().filter(s -> s.isAvailable(start, end)).toList();
    }

    /** Shared vehicles at the Hub, all types if type is null. */
    @JsonIgnore
    public List<Vehicle> getVehicles(VehicleType type) {
        return vehicles.stream().filter(v -> type == null || v.getType() == type).toList();
    }

    public int countFreeSlots() {
        return (int) parkingSlots.stream().filter(s -> s.getStatus() == SlotStatus.AVAILABLE).count();
    }

    public int countFreePorts() {
        return (int) chargingPorts.stream().filter(ChargingPort::isFree).count();
    }

    public int waitingCount() {
        return waitingQueue == null ? 0 : waitingQueue.size();
    }

    /** Utilisation of parking + charging resources, waiting requests counted as demand. */
    public double utilisation() {
        int total = parkingSlots.size() + chargingPorts.size();
        if (total == 0) return 0;
        long usedSlots = parkingSlots.stream().filter(s -> s.getStatus() != SlotStatus.AVAILABLE).count();
        long busyPorts = chargingPorts.stream().filter(p -> !p.isFree()).count();
        return (usedSlots + busyPorts + waitingCount()) / (double) total;
    }

    // ---- F-06 / F-07 ------------------------------------------------------------------

    /** Charging ports of the Hub in the given area (all areas if area is null/blank). */
    @JsonIgnore
    public List<ChargingPort> getPorts(String area) {
        return chargingPorts.stream()
                .filter(p -> area == null || area.isBlank() || area.equalsIgnoreCase(p.getArea()))
                .toList();
    }

    /** True if the number of waiting requests is above the overload threshold of the Hub. */
    public boolean isOverloaded(int waitingCount) { return waitingCount > overloadThreshold; }

    public long countWorkingPorts() {
        return chargingPorts.stream().filter(p -> p.getStatus() != PortStatus.FIXING).count();
    }

    // ---- F-08 -------------------------------------------------------------------------

    @JsonIgnore
    public List<Vehicle> getAvailableVehicles() {
        return vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.AVAILABLE).toList();
    }

    public int countAvailable() { return getAvailableVehicles().size(); }

    public boolean hasEnoughAvailable(int quantity) { return countAvailable() >= quantity; }

    /** Available vehicles minus demand; negative = shortage, positive = surplus. */
    public int getBalanceGap() { return countAvailable() - expectedDemand; }

    public boolean isActive() { return state == HubState.ACTIVE; }

    public void touch() { this.lastUpdated = LocalDateTime.now(); }

    // ---- getters / setters --------------------------------------------------------------

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public HubState getState() { return state; }
    public void setState(HubState state) { this.state = state; }
    public int getExpectedDemand() { return expectedDemand; }
    public void setExpectedDemand(int expectedDemand) { this.expectedDemand = expectedDemand; }
    public int getOverloadThreshold() { return overloadThreshold; }
    public void setOverloadThreshold(int overloadThreshold) { this.overloadThreshold = overloadThreshold; }
    public int getOpenHour() { return openHour; }
    public void setOpenHour(int openHour) { this.openHour = openHour; }
    public int getCloseHour() { return closeHour; }
    public void setCloseHour(int closeHour) { this.closeHour = closeHour; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    @JsonIgnore
    public List<Vehicle> getAllVehicles() { return vehicles; }
    @JsonIgnore
    public WaitingQueue getWaitingQueue() { return waitingQueue; }
}

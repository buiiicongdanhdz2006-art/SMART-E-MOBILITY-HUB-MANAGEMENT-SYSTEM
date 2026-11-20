package vn.edu.hcmut.emobility.fleet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.hcmut.emobility.common.enums.DispatchStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** Order to move vehicles from a surplus Hub to a shortage Hub (UC-F08-04, 05). */
@Entity
@Table(name = "dispatch_orders")
public class DispatchOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Hub fromHub;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Hub toHub;

    private int quantity;

    @ManyToMany
    @JoinTable(name = "dispatch_order_vehicles",
            joinColumns = @JoinColumn(name = "order_id"), inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    private List<Vehicle> vehicles = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dispatch_arrived", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "vehicle_id")
    private Set<Long> arrivedVehicleIds = new HashSet<>();

    /** vehicleId -&gt; incidentId of the vehicles that will not arrive. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dispatch_flagged", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "vehicle_id")
    @Column(name = "incident_id")
    private Map<Long, Long> flaggedVehicles = new HashMap<>();

    @Enumerated(EnumType.STRING)
    private DispatchStatus status = DispatchStatus.PENDING;

    private Long createdBy;
    private String note;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime plannedArrival;
    private LocalDateTime endTime;

    protected DispatchOrder() {}

    public DispatchOrder(Hub fromHub, Hub toHub, List<Vehicle> vehicles, Long createdBy, LocalDateTime plannedArrival, String note) {
        this.fromHub = fromHub;
        this.toHub = toHub;
        this.vehicles = new ArrayList<>(vehicles);
        this.quantity = vehicles.size();
        this.createdBy = createdBy;
        this.plannedArrival = plannedArrival;
        this.note = note;
    }

    /** Sets the selected vehicles to IN_TRANSIT and the order to IN_PROGRESS. */
    public void start() {
        if (status != DispatchStatus.PENDING) throw new IllegalStateException("Lệnh điều phối đã bắt đầu");
        for (Vehicle v : vehicles) {
            v.updateStatus(VehicleStatus.IN_TRANSIT);
            v.leaveHub();
        }
        this.status = DispatchStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /** Moves vehicle v to the destination Hub and sets it AVAILABLE. */
    public void markVehicleArrived(Vehicle v) {
        if (!contains(v)) throw new IllegalArgumentException("Xe " + v.getCode() + " không thuộc lệnh điều phối #" + id);
        if (arrivedVehicleIds.contains(v.getId()) || flaggedVehicles.containsKey(v.getId())) return;
        v.moveToHub(toHub);
        v.updateStatus(VehicleStatus.AVAILABLE);
        arrivedVehicleIds.add(v.getId());
    }

    /** Marks vehicle v as affected by incident i, so it is not expected to arrive. */
    public void flagIncident(Vehicle v, Long incidentId) {
        if (!contains(v)) throw new IllegalArgumentException("Xe " + v.getCode() + " không thuộc lệnh điều phối #" + id);
        flaggedVehicles.put(v.getId(), incidentId);
    }

    /** COMPLETED when every vehicle has arrived or been flagged; records the end time. */
    public void complete() {
        if (!isAllDone()) throw new IllegalStateException("Còn xe đang di chuyển");
        this.status = DispatchStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
    }

    public boolean isAllDone() { return arrivedVehicleIds.size() + flaggedVehicles.size() >= vehicles.size(); }

    /** Ratio of arrived vehicles to total vehicles. */
    public double getProgress() {
        return vehicles.isEmpty() ? 1.0 : Math.round(arrivedVehicleIds.size() * 100.0 / vehicles.size()) / 100.0;
    }

    public boolean isPending(Vehicle v) {
        return !arrivedVehicleIds.contains(v.getId()) && !flaggedVehicles.containsKey(v.getId());
    }

    private boolean contains(Vehicle v) { return vehicles.stream().anyMatch(x -> x.getId().equals(v.getId())); }

    public Long getId() { return id; }
    @JsonIgnore
    public Hub getFromHub() { return fromHub; }
    public Long getFromHubId() { return fromHub.getId(); }
    public String getFromHubName() { return fromHub.getName(); }
    @JsonIgnore
    public Hub getToHub() { return toHub; }
    public Long getToHubId() { return toHub.getId(); }
    public String getToHubName() { return toHub.getName(); }
    public int getQuantity() { return quantity; }
    public List<Vehicle> getVehicles() { return vehicles; }
    public Set<Long> getArrivedVehicleIds() { return arrivedVehicleIds; }
    public Map<Long, Long> getFlaggedVehicles() { return flaggedVehicles; }
    public DispatchStatus getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getPlannedArrival() { return plannedArrival; }
    public LocalDateTime getEndTime() { return endTime; }
}

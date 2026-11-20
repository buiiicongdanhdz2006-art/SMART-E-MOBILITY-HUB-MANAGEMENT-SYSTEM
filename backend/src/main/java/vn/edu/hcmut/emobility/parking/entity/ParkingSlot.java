package vn.edu.hcmut.emobility.parking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import vn.edu.hcmut.emobility.common.enums.SlotStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;

@Entity
@Table(name = "parking_slots")
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @JsonIgnore
    @ManyToOne
    private Hub hub;

    @Enumerated(EnumType.STRING)
    private SlotStatus status = SlotStatus.AVAILABLE;

    /** Last occupancy reported by the slot sensor (F-12); null if no sensor data yet. */
    private Boolean sensorOccupied;
    private LocalDateTime sensorUpdatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "slot")
    private List<ParkingReservation> reservations = new ArrayList<>();

    protected ParkingSlot() {}

    public ParkingSlot(String code, Hub hub) {
        this.code = code;
        this.hub = hub;
    }

    /** True if the slot has no active reservation in [start, end] and is not occupied by an unknown car now. */
    public boolean isAvailable(LocalDateTime start, LocalDateTime end) {
        boolean overlap = reservations.stream().anyMatch(r -> r.isActive() && r.overlaps(start, end));
        if (overlap) return false;
        LocalDateTime now = LocalDateTime.now();
        boolean coversNow = !start.isAfter(now) && end.isAfter(now);
        return !(coversNow && status == SlotStatus.OCCUPIED);
    }

    public boolean hasActiveReservation() {
        return reservations.stream().anyMatch(ParkingReservation::isActive);
    }

    public void updateStatus(SlotStatus s) { this.status = s; }

    public void updateSensor(boolean occupied) {
        this.sensorOccupied = occupied;
        this.sensorUpdatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    @JsonIgnore
    public Hub getHub() { return hub; }
    public Long getHubId() { return hub == null ? null : hub.getId(); }
    public SlotStatus getStatus() { return status; }
    public Boolean getSensorOccupied() { return sensorOccupied; }
    public LocalDateTime getSensorUpdatedAt() { return sensorUpdatedAt; }
    @JsonIgnore
    public List<ParkingReservation> getReservations() { return reservations; }
}

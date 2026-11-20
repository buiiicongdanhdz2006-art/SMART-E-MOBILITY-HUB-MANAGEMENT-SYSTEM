package vn.edu.hcmut.emobility.parking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.common.enums.ReservationStatus;

@Entity
@Table(name = "parking_reservations")
public class ParkingReservation {

    /** A reservation not checked in this long after its start time expires. */
    public static final int GRACE_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private User owner;

    @JsonIgnore
    @ManyToOne(optional = false)
    private PersonalVehicle vehicle;

    @JsonIgnore
    @ManyToOne(optional = false)
    private ParkingSlot slot;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.RESERVED;

    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private boolean reminderSent;
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ParkingReservation() {}

    public ParkingReservation(User owner, PersonalVehicle vehicle, ParkingSlot slot, LocalDateTime start, LocalDateTime end) {
        this.owner = owner;
        this.vehicle = vehicle;
        this.slot = slot;
        this.startTime = start;
        this.endTime = end;
    }

    /** True if the reservation is RESERVED and has not expired. */
    public boolean isValid(LocalDateTime now) {
        return status == ReservationStatus.RESERVED
                && !now.isAfter(startTime.plusMinutes(GRACE_MINUTES))
                && now.isBefore(endTime);
    }

    public boolean isExpired(LocalDateTime now) {
        return status == ReservationStatus.RESERVED && now.isAfter(startTime.plusMinutes(GRACE_MINUTES));
    }

    public boolean isActive() {
        return status == ReservationStatus.RESERVED || status == ReservationStatus.CHECKED_IN;
    }

    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return startTime.isBefore(end) && endTime.isAfter(start);
    }

    public void checkIn(LocalDateTime now) {
        this.status = ReservationStatus.CHECKED_IN;
        this.checkInTime = now;
    }

    public void checkOut(LocalDateTime now) {
        this.status = ReservationStatus.COMPLETED;
        this.checkOutTime = now;
    }

    public void cancel() {
        if (status != ReservationStatus.RESERVED) throw new IllegalStateException("Chỉ huỷ được đặt chỗ chưa check-in");
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() { this.status = ReservationStatus.EXPIRED; }

    public Long getId() { return id; }
    @JsonIgnore
    public User getOwner() { return owner; }
    public Long getOwnerId() { return owner.getId(); }
    @JsonIgnore
    public PersonalVehicle getVehicle() { return vehicle; }
    public String getVehiclePlate() { return vehicle.getPlate(); }
    @JsonIgnore
    public ParkingSlot getSlot() { return slot; }
    public String getSlotCode() { return slot.getCode(); }
    public Long getHubId() { return slot.getHubId(); }
    public String getHubName() { return slot.getHub() == null ? null : slot.getHub().getName(); }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getCheckInTime() { return checkInTime; }
    public LocalDateTime getCheckOutTime() { return checkOutTime; }
    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

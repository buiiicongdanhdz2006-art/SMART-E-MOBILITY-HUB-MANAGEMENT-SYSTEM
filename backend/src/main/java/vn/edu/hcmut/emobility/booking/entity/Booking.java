package vn.edu.hcmut.emobility.booking.entity;

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
import vn.edu.hcmut.emobility.common.enums.BookingStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** Vehicle booking (F-04). */
@Entity
@Table(name = "bookings")
public class Booking {

    /** A booking not picked up this long after the pickup time expires. */
    public static final int NO_SHOW_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private User user;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Vehicle vehicle;

    @JsonIgnore
    @ManyToOne
    private Hub pickupHub;

    @JsonIgnore
    @ManyToOne
    private Hub destinationHub;

    private LocalDateTime pickupTime;
    private LocalDateTime returnTime;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.RESERVED;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime endedAt;
    private boolean pickupReminderSent;
    private boolean returnReminderSent;

    protected Booking() {}

    public Booking(User user, Vehicle vehicle, Hub pickupHub, Hub destinationHub, LocalDateTime pickupTime, LocalDateTime returnTime) {
        this.user = user;
        this.vehicle = vehicle;
        this.pickupHub = pickupHub;
        this.destinationHub = destinationHub;
        this.pickupTime = pickupTime;
        this.returnTime = returnTime;
    }

    /** True if the status is RESERVED and the pickup time has not passed. */
    public boolean canCancel(LocalDateTime now) {
        return status == BookingStatus.RESERVED && now.isBefore(pickupTime);
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
        this.endedAt = LocalDateTime.now();
    }

    public void startUse() { this.status = BookingStatus.IN_PROGRESS; }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = BookingStatus.EXPIRED;
        this.endedAt = LocalDateTime.now();
    }

    public boolean isNoShow(LocalDateTime now) {
        return status == BookingStatus.RESERVED && now.isAfter(pickupTime.plusMinutes(NO_SHOW_MINUTES));
    }

    public Long getId() { return id; }
    @JsonIgnore
    public User getUser() { return user; }
    public Long getUserId() { return user.getId(); }
    @JsonIgnore
    public Vehicle getVehicle() { return vehicle; }
    public Long getVehicleId() { return vehicle.getId(); }
    public String getVehicleCode() { return vehicle.getCode(); }
    public String getVehicleType() { return vehicle.getType() == null ? null : vehicle.getType().name(); }
    @JsonIgnore
    public Hub getPickupHub() { return pickupHub; }
    public String getPickupHubName() { return pickupHub == null ? null : pickupHub.getName(); }
    @JsonIgnore
    public Hub getDestinationHub() { return destinationHub; }
    public String getDestinationHubName() { return destinationHub == null ? null : destinationHub.getName(); }
    public LocalDateTime getPickupTime() { return pickupTime; }
    public LocalDateTime getReturnTime() { return returnTime; }
    public BookingStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public boolean isPickupReminderSent() { return pickupReminderSent; }
    public void setPickupReminderSent(boolean v) { this.pickupReminderSent = v; }
    public boolean isReturnReminderSent() { return returnReminderSent; }
    public void setReturnReminderSent(boolean v) { this.returnReminderSent = v; }
}

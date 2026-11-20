package vn.edu.hcmut.emobility.booking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** Actual use of a booked vehicle, from pickup to return (F-04). */
@Entity
@Table(name = "rentals")
public class Rental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(optional = false)
    private Booking booking;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer startBattery;
    private Integer endBattery;

    @JsonIgnore
    @ManyToOne
    private Hub returnHub;

    protected Rental() {}

    public Rental(Booking booking) {
        this.booking = booking;
    }

    /** Records the rental start time. */
    public void start() {
        this.startTime = LocalDateTime.now();
        this.startBattery = booking.getVehicle().getBatteryLevel();
    }

    /** Records the rental end time and the return Hub. */
    public void end(Hub returnHub) {
        if (endTime != null) throw new IllegalStateException("Lượt thuê đã kết thúc");
        this.endTime = LocalDateTime.now();
        this.returnHub = returnHub;
        this.endBattery = booking.getVehicle().getBatteryLevel();
    }

    public boolean isEnded() { return endTime != null; }

    public long getDurationMinutes() {
        if (startTime == null) return 0;
        return Duration.between(startTime, endTime == null ? LocalDateTime.now() : endTime).toMinutes();
    }

    public Long getId() { return id; }
    @JsonIgnore
    public Booking getBooking() { return booking; }
    public Long getBookingId() { return booking.getId(); }
    public String getVehicleCode() { return booking.getVehicleCode(); }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Integer getStartBattery() { return startBattery; }
    public Integer getEndBattery() { return endBattery; }
    @JsonIgnore
    public Hub getReturnHub() { return returnHub; }
    public String getReturnHubName() { return returnHub == null ? null : returnHub.getName(); }
}

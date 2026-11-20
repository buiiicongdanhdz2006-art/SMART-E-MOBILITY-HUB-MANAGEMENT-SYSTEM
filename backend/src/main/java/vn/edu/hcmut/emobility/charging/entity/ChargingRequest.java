package vn.edu.hcmut.emobility.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.common.enums.PriorityLevel;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/**
 * Charging request (F-06). State chart:
 * CREATED -> ASSIGNED | WAITING; WAITING -> ASSIGNED; ASSIGNED -> CHARGING | EXPIRED | WAITING (port broke);
 * CHARGING -> COMPLETED | WAITING (port broke); CREATED/WAITING/ASSIGNED -> CANCELLED.
 */
@Entity
@Table(name = "charging_requests")
public class ChargingRequest {

    /** An ASSIGNED request expires if the vehicle is not plugged in within this time. */
    public static final int ASSIGN_TIMEOUT_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Account user;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Hub hub;

    private String area;

    /** Shared vehicle being charged (null if it is a personal vehicle). */
    @JsonIgnore
    @ManyToOne
    private Vehicle vehicle;

    /** Personal vehicle being charged (null if it is a shared vehicle). */
    @JsonIgnore
    @ManyToOne
    private PersonalVehicle personalVehicle;

    private int batteryLevel;
    private int targetBattery = 100;
    private LocalDateTime nextUsageTime;
    private LocalDateTime startTime;
    /** Expected charging duration in minutes. */
    private int expectedDuration;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.CREATED;

    @Enumerated(EnumType.STRING)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    private boolean urgent;
    private String urgentReason;

    @JsonIgnore
    @ManyToOne
    private ChargingPort assignedPort;

    private LocalDateTime assignedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Embedded
    private ChargingSession session;

    protected ChargingRequest() {}

    public ChargingRequest(Account user, Hub hub, String area, Vehicle vehicle, PersonalVehicle personalVehicle,
                           int batteryLevel, int targetBattery, LocalDateTime nextUsageTime,
                           LocalDateTime startTime, int expectedDuration) {
        this.user = user;
        this.hub = hub;
        this.area = area;
        this.vehicle = vehicle;
        this.personalVehicle = personalVehicle;
        this.batteryLevel = batteryLevel;
        this.targetBattery = targetBattery;
        this.nextUsageTime = nextUsageTime;
        this.startTime = startTime;
        this.expectedDuration = expectedDuration;
    }

    /** Assigns the port, CREATED or WAITING -> ASSIGNED. */
    public void assignPort(ChargingPort port) {
        if (status != RequestStatus.CREATED && status != RequestStatus.WAITING) {
            throw new IllegalStateException("Yêu cầu #" + id + " đang " + status + ", không thể gán cổng");
        }
        this.assignedPort = port;
        this.assignedAt = LocalDateTime.now();
        this.status = RequestStatus.ASSIGNED;
        touch();
    }

    /** Back to WAITING: no free port, or the assigned port broke before / during charging. */
    public void moveToWaiting() {
        if (status == RequestStatus.COMPLETED || status == RequestStatus.CANCELLED || status == RequestStatus.EXPIRED) {
            throw new IllegalStateException("Yêu cầu #" + id + " đã kết thúc");
        }
        this.assignedPort = null;
        this.assignedAt = null;
        this.status = RequestStatus.WAITING;
        touch();
    }

    /** ASSIGNED -> CHARGING when the vehicle is plugged in. */
    public void startCharging() {
        if (status != RequestStatus.ASSIGNED) throw new IllegalStateException("Yêu cầu #" + id + " chưa được gán cổng");
        this.status = RequestStatus.CHARGING;
        touch();
    }

    /** WAITING or ASSIGNED -> CANCELLED; rejected while CHARGING. */
    public void cancel() {
        if (status == RequestStatus.CHARGING) throw new IllegalStateException("Không thể huỷ khi xe đang sạc, hãy dừng sạc");
        if (status != RequestStatus.WAITING && status != RequestStatus.ASSIGNED && status != RequestStatus.CREATED) {
            throw new IllegalStateException("Yêu cầu #" + id + " đã kết thúc");
        }
        this.status = RequestStatus.CANCELLED;
        touch();
    }

    /** ASSIGNED -> EXPIRED when the vehicle is not plugged in in time. */
    public void expire() {
        if (status != RequestStatus.ASSIGNED) throw new IllegalStateException("Chỉ yêu cầu ASSIGNED mới hết hạn");
        this.status = RequestStatus.EXPIRED;
        touch();
    }

    public void complete() {
        if (status != RequestStatus.CHARGING) throw new IllegalStateException("Yêu cầu #" + id + " không ở trạng thái sạc");
        this.status = RequestStatus.COMPLETED;
        touch();
    }

    /** True if ASSIGNED for more than 15 minutes since its start time (or assignment, whichever is later). */
    public boolean isOverdue(LocalDateTime now) {
        if (status != RequestStatus.ASSIGNED || assignedAt == null) return false;
        LocalDateTime from = startTime != null && startTime.isAfter(assignedAt) ? startTime : assignedAt;
        return now.isAfter(from.plusMinutes(ASSIGN_TIMEOUT_MINUTES));
    }

    /** Priority Override by an Operator, with the reason entered. */
    public void markUrgent(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Phải nhập lý do khẩn cấp");
        this.urgent = true;
        this.urgentReason = reason.trim();
        this.priority = PriorityLevel.HIGH;
        touch();
    }

    public boolean isPending() {
        return status == RequestStatus.CREATED || status == RequestStatus.WAITING || status == RequestStatus.ASSIGNED;
    }

    /** Identifier the IoT sensor reports when the vehicle is plugged in: shared vehicle code or plate. */
    public String getVehicleRef() {
        if (vehicle != null) return vehicle.getCode();
        if (personalVehicle != null) return personalVehicle.getPlate();
        return null;
    }

    private void touch() { this.updatedAt = LocalDateTime.now(); }

    public void startSession(LocalDateTime now) {
        this.session = new ChargingSession();
        this.session.start(batteryLevel, targetBattery, expectedDuration, now);
    }

    public Long getId() { return id; }
    @JsonIgnore
    public Account getUser() { return user; }
    public Long getUserId() { return user.getId(); }
    @JsonIgnore
    public Hub getHub() { return hub; }
    public Long getHubId() { return hub.getId(); }
    public String getArea() { return area; }
    @JsonIgnore
    public Vehicle getVehicle() { return vehicle; }
    @JsonIgnore
    public PersonalVehicle getPersonalVehicle() { return personalVehicle; }
    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    public int getTargetBattery() { return targetBattery; }
    public LocalDateTime getNextUsageTime() { return nextUsageTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public int getExpectedDuration() { return expectedDuration; }
    public RequestStatus getStatus() { return status; }
    public PriorityLevel getPriority() { return priority; }
    public void setPriority(PriorityLevel priority) { this.priority = urgent ? PriorityLevel.HIGH : priority; }
    public boolean isUrgent() { return urgent; }
    public String getUrgentReason() { return urgentReason; }
    @JsonIgnore
    public ChargingPort getAssignedPort() { return assignedPort; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ChargingSession getSession() { return session; }

    /** For tests / seeding only. */
    public void setIdForTest(Long id) { this.id = id; }
    public void setCreatedAtForTest(LocalDateTime t) { this.createdAt = t; }
}

package vn.edu.hcmut.emobility.incident.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.common.enums.IncidentStatus;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/**
 * Operational incident (F-09). State chart: REPORTED -&gt; INVESTIGATING -&gt; MAINTENANCE -&gt; RESOLVED,
 * REPORTED / INVESTIGATING -&gt; INVALID_CLOSED (false alarm).
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    private SeverityLevel severity = SeverityLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    private IncidentStatus status = IncidentStatus.REPORTED;

    @JsonIgnore
    @ManyToOne
    private Hub hub;

    @JsonIgnore
    @ManyToOne
    private Vehicle vehicle;

    @JsonIgnore
    @ManyToOne
    private ChargingPort port;

    private String title;

    @Column(length = 2000)
    private String description;

    private Long reportedBy;
    private boolean autoDetected;
    private Long dispatchOrderId;
    private String invalidReason;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "incident_id")
    @OrderBy("at ASC")
    private List<IncidentStatusLog> statusLogs = new ArrayList<>();

    protected Incident() {}

    public Incident(IncidentType type, SeverityLevel severity, Hub hub, Vehicle vehicle, ChargingPort port,
                    String title, String description, Long reportedBy, boolean autoDetected) {
        this.type = type;
        this.severity = severity == null ? SeverityLevel.MEDIUM : severity;
        this.hub = hub;
        this.vehicle = vehicle;
        this.port = port;
        this.title = title;
        this.description = description;
        this.reportedBy = reportedBy;
        this.autoDetected = autoDetected;
        logTransition(null, IncidentStatus.REPORTED, reportedBy, autoDetected ? "Tự động phát hiện" : "Báo cáo sự cố");
    }

    /** REPORTED -&gt; INVESTIGATING, records the time and Operator. */
    public void investigate(Long operatorId) {
        require(IncidentStatus.REPORTED);
        change(IncidentStatus.INVESTIGATING, operatorId, "Bắt đầu kiểm tra");
    }

    /** Closes a false alarm as INVALID_CLOSED and stores the reason. */
    public void markInvalid(Long operatorId, String reason) {
        if (status != IncidentStatus.REPORTED && status != IncidentStatus.INVESTIGATING) {
            throw new IllegalStateException("Chỉ đóng báo động giả khi sự cố đang REPORTED / INVESTIGATING");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Phải nhập lý do");
        this.invalidReason = reason.trim();
        change(IncidentStatus.INVALID_CLOSED, operatorId, "Báo động giả: " + reason.trim());
    }

    /** -&gt; MAINTENANCE. The service sets the vehicle to MAINTENANCE / the port to FIXING. */
    public void assignToMaintenance(Long operatorId) {
        if (status != IncidentStatus.INVESTIGATING && status != IncidentStatus.REPORTED) {
            throw new IllegalStateException("Sự cố đang " + status + ", không thể chuyển bảo trì");
        }
        change(IncidentStatus.MAINTENANCE, operatorId, "Chuyển cho bộ phận bảo trì");
    }

    /** -&gt; RESOLVED. The service sets the vehicle back to AVAILABLE / the port back to AVAILABLE. */
    public void resolve(Long operatorId, String note) {
        if (status != IncidentStatus.MAINTENANCE && status != IncidentStatus.INVESTIGATING) {
            throw new IllegalStateException("Sự cố đang " + status + ", không thể đánh dấu đã xử lý");
        }
        change(IncidentStatus.RESOLVED, operatorId, note == null || note.isBlank() ? "Đã xử lý" : note.trim());
    }

    public List<IncidentStatusLog> getStatusHistory() { return Collections.unmodifiableList(statusLogs); }

    /** Adds a status log entry; called by every status change. */
    public void logTransition(IncidentStatus from, IncidentStatus to, Long operatorId, String note) {
        statusLogs.add(new IncidentStatusLog(from, to, operatorId, note));
    }

    public boolean isOpen() {
        return status != IncidentStatus.RESOLVED && status != IncidentStatus.INVALID_CLOSED;
    }

    private void change(IncidentStatus to, Long operatorId, String note) {
        IncidentStatus from = status;
        this.status = to;
        this.updatedAt = LocalDateTime.now();
        logTransition(from, to, operatorId, note);
    }

    private void require(IncidentStatus s) {
        if (status != s) throw new IllegalStateException("Sự cố đang " + status + ", cần " + s);
    }

    public Long getId() { return id; }
    public IncidentType getType() { return type; }
    public SeverityLevel getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    @JsonIgnore
    public Hub getHub() { return hub; }
    public Long getHubId() { return hub == null ? null : hub.getId(); }
    public String getHubName() { return hub == null ? null : hub.getName(); }
    @JsonIgnore
    public Vehicle getVehicle() { return vehicle; }
    public Long getVehicleId() { return vehicle == null ? null : vehicle.getId(); }
    public String getVehicleCode() { return vehicle == null ? null : vehicle.getCode(); }
    @JsonIgnore
    public ChargingPort getPort() { return port; }
    public Long getPortId() { return port == null ? null : port.getId(); }
    public String getPortCode() { return port == null ? null : port.getCode(); }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Long getReportedBy() { return reportedBy; }
    public boolean isAutoDetected() { return autoDetected; }
    public Long getDispatchOrderId() { return dispatchOrderId; }
    public void setDispatchOrderId(Long dispatchOrderId) { this.dispatchOrderId = dispatchOrderId; }
    public String getInvalidReason() { return invalidReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

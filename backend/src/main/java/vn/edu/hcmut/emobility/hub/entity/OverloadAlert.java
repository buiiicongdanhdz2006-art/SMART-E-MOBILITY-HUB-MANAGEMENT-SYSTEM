package vn.edu.hcmut.emobility.hub.entity;

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
import vn.edu.hcmut.emobility.common.enums.AlertStatus;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.LoadLevel;

/** Alert raised when a Hub reaches the OVERLOADED level (UC-F02-04). */
@Entity
@Table(name = "overload_alerts")
public class OverloadAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    private Hub hub;

    @Enumerated(EnumType.STRING)
    private AlertType type = AlertType.HUB_OVERLOAD;

    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_level")
    private LoadLevel level;

    private double utilisation;
    private String message;
    private LocalDateTime createdAt = LocalDateTime.now();
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolveNote;

    protected OverloadAlert() {}

    public OverloadAlert(Hub hub, LoadLevel level, double utilisation, String message) {
        this.hub = hub;
        this.level = level;
        this.utilisation = utilisation;
        this.message = message;
    }

    /** Sets the status to RESOLVED and records the Operator, time and note. */
    public void resolve(Long operatorId, LocalDateTime time, String note) {
        if (status == AlertStatus.RESOLVED) throw new IllegalStateException("Cảnh báo đã được xử lý");
        this.status = AlertStatus.RESOLVED;
        this.resolvedBy = operatorId;
        this.resolvedAt = time;
        this.resolveNote = note;
    }

    public Long getId() { return id; }
    @JsonIgnore
    public Hub getHub() { return hub; }
    public Long getHubId() { return hub == null ? null : hub.getId(); }
    public String getHubName() { return hub == null ? null : hub.getName(); }
    public AlertType getType() { return type; }
    public AlertStatus getStatus() { return status; }
    public LoadLevel getLevel() { return level; }
    public double getUtilisation() { return utilisation; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolveNote() { return resolveNote; }
}

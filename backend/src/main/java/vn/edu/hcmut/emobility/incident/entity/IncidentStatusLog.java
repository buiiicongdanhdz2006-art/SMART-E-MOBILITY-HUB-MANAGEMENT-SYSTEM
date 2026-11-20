package vn.edu.hcmut.emobility.incident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.common.enums.IncidentStatus;

/** One status change of an incident (composition of Incident). */
@Entity
@Table(name = "incident_status_logs")
public class IncidentStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private IncidentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private IncidentStatus toStatus;

    private Long operatorId;

    @Column(length = 1000)
    private String note;

    private LocalDateTime at = LocalDateTime.now();

    protected IncidentStatusLog() {}

    public IncidentStatusLog(IncidentStatus from, IncidentStatus to, Long operatorId, String note) {
        this.fromStatus = from;
        this.toStatus = to;
        this.operatorId = operatorId;
        this.note = note;
    }

    public Long getId() { return id; }
    public IncidentStatus getFromStatus() { return fromStatus; }
    public IncidentStatus getToStatus() { return toStatus; }
    public Long getOperatorId() { return operatorId; }
    public String getNote() { return note; }
    public LocalDateTime getAt() { return at; }
}

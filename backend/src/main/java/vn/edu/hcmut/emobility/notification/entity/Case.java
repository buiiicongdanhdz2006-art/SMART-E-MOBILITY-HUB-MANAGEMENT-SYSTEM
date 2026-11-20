package vn.edu.hcmut.emobility.notification.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.Severity;

/** Case handled on the Monitoring Dashboard (F-11): parent of Alert and Complaint. Entity name avoids the JPQL keyword CASE. */
@Entity(name = "SupportCase")
@Table(name = "cases")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "case_type")
public abstract class Case {

    private static final Map<CaseStatus, Set<CaseStatus>> TRANSITIONS = new EnumMap<>(CaseStatus.class);

    static {
        TRANSITIONS.put(CaseStatus.OPEN, EnumSet.of(CaseStatus.DELIVERING, CaseStatus.IN_PROGRESS, CaseStatus.ESCALATED, CaseStatus.RESOLVED));
        TRANSITIONS.put(CaseStatus.DELIVERING, EnumSet.of(CaseStatus.IN_PROGRESS, CaseStatus.DELIVERY_FAILED));
        TRANSITIONS.put(CaseStatus.DELIVERY_FAILED, EnumSet.of(CaseStatus.DELIVERING, CaseStatus.ESCALATED, CaseStatus.RESOLVED));
        TRANSITIONS.put(CaseStatus.IN_PROGRESS, EnumSet.of(CaseStatus.ESCALATED, CaseStatus.RESOLVED));
        TRANSITIONS.put(CaseStatus.ESCALATED, EnumSet.of(CaseStatus.IN_PROGRESS, CaseStatus.RESOLVED));
        TRANSITIONS.put(CaseStatus.RESOLVED, EnumSet.noneOf(CaseStatus.class));
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private CaseStatus status = CaseStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private Severity severity = Severity.MEDIUM;

    private Long ownerId;
    private Long createdBy;
    private Long relatedUserId;
    private Long hubId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_action_log", joinColumns = @JoinColumn(name = "case_id"))
    @OrderColumn(name = "idx")
    @Column(name = "note", length = 1000)
    private List<String> actionLog = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;

    protected Case() {}

    protected Case(String title, String description, Severity severity, Long createdBy, Long relatedUserId, Long hubId) {
        this.title = title;
        this.description = description;
        this.severity = severity == null ? Severity.MEDIUM : severity;
        this.createdBy = createdBy;
        this.ownerId = createdBy;
        this.relatedUserId = relatedUserId;
        this.hubId = hubId;
    }

    /** Changes the case to a new status according to the state diagram. */
    public void updateStatus(CaseStatus newStatus) {
        if (status == newStatus) return;
        if (!TRANSITIONS.get(status).contains(newStatus)) {
            throw new IllegalStateException("Không thể chuyển trường hợp #" + id + " từ " + status + " sang " + newStatus);
        }
        this.status = newStatus;
    }

    /** Adds a corrective-action note to actionLog. */
    public void addActionNote(String note) {
        actionLog.add(LocalDateTime.now().withNano(0) + " - " + note);
    }

    /** Assigns the case to another operator or team after escalation (F11-AF-02). */
    public void changeOwner(Long newOwnerId) { this.ownerId = newOwnerId; }

    /** RESOLVED and stores the resolvedAt timestamp (F11-POST-04). */
    public void markResolved() {
        updateStatus(CaseStatus.RESOLVED);
        this.resolvedAt = LocalDateTime.now();
    }

    public abstract String getCaseType();

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public CaseStatus getStatus() { return status; }
    public Severity getSeverity() { return severity; }
    public Long getOwnerId() { return ownerId; }
    public Long getCreatedBy() { return createdBy; }
    public Long getRelatedUserId() { return relatedUserId; }
    public Long getHubId() { return hubId; }
    public List<String> getActionLog() { return actionLog; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}

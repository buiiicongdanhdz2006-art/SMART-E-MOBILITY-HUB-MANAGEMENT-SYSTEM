package vn.edu.hcmut.emobility.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
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
import java.util.Comparator;
import java.util.List;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** Charging schedule of a Hub, contains PortAssignment (composition) - F-07. */
@Entity
@Table(name = "charging_schedules")
public class ChargingSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Hub hub;

    private LocalDateTime createdAt = LocalDateTime.now();
    private Long createdBy;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private boolean overloaded;
    private int waitingCount;
    private String note;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "schedule_id")
    @OrderBy("plannedStart ASC")
    private List<PortAssignment> assignments = new ArrayList<>();

    protected ChargingSchedule() {}

    public ChargingSchedule(Hub hub, Long createdBy, LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.hub = hub;
        this.createdBy = createdBy;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public void addAssignment(PortAssignment a) { assignments.add(a); }

    /** Port assignments ordered by planned start time. */
    public List<PortAssignment> getAssignments() {
        List<PortAssignment> list = new ArrayList<>(assignments);
        list.sort(Comparator.comparing(PortAssignment::getPlannedStart));
        return list;
    }

    public Long getId() { return id; }
    @JsonIgnore
    public Hub getHub() { return hub; }
    public Long getHubId() { return hub.getId(); }
    public String getHubName() { return hub.getName(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public boolean isOverloaded() { return overloaded; }
    public void setOverloaded(boolean overloaded) { this.overloaded = overloaded; }
    public int getWaitingCount() { return waitingCount; }
    public void setWaitingCount(int waitingCount) { this.waitingCount = waitingCount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

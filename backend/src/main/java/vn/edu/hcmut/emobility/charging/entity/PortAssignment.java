package vn.edu.hcmut.emobility.charging.entity;

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
import vn.edu.hcmut.emobility.common.enums.PriorityLevel;

/**
 * Result of allocating a port to a request. immediate = true means the port was really assigned
 * now (request ASSIGNED); false means an estimated slot once the port becomes free (request WAITING).
 */
@Entity
@Table(name = "port_assignments")
public class PortAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private ChargingRequest request;

    @JsonIgnore
    @ManyToOne(optional = false)
    private ChargingPort port;

    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;

    @Enumerated(EnumType.STRING)
    private PriorityLevel priority;

    private boolean immediate;

    protected PortAssignment() {}

    public PortAssignment(ChargingRequest request, ChargingPort port, LocalDateTime plannedStart,
                          LocalDateTime plannedEnd, boolean immediate) {
        this.request = request;
        this.port = port;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.priority = request.getPriority();
        this.immediate = immediate;
    }

    public Long getId() { return id; }
    @JsonIgnore
    public ChargingRequest getRequest() { return request; }
    public Long getRequestId() { return request.getId(); }
    public Long getUserId() { return request.getUserId(); }
    public String getVehicleRef() { return request.getVehicleRef(); }
    public boolean isUrgent() { return request.isUrgent(); }
    @JsonIgnore
    public ChargingPort getPort() { return port; }
    public Long getPortId() { return port.getId(); }
    public String getPortCode() { return port.getCode(); }
    public LocalDateTime getPlannedStart() { return plannedStart; }
    public LocalDateTime getPlannedEnd() { return plannedEnd; }
    public PriorityLevel getPriority() { return priority; }
    public boolean isImmediate() { return immediate; }
}

package vn.edu.hcmut.emobility.charging.entity;

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
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/**
 * Charging port of a Hub (F-06). State chart: AVAILABLE -> CHARGING -> AVAILABLE, any -> FIXING -> AVAILABLE.
 * While a request is ASSIGNED (vehicle not plugged in yet) the port stays AVAILABLE but is reserved
 * for that request ({@link #getReservedRequestId()}), so it is not given to another request.
 */
@Entity
@Table(name = "charging_ports")
public class ChargingPort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Hub hub;

    /** Area of the Hub where the port is located, e.g. "A" or "B". */
    private String area;

    private double powerKw = 7.0;

    @Enumerated(EnumType.STRING)
    private PortStatus status = PortStatus.AVAILABLE;

    private Long reservedRequestId;
    private LocalDateTime lastReadingAt;
    private Double lastVoltage;
    private Double lastCurrent;
    private Double lastTemperature;

    protected ChargingPort() {}

    public ChargingPort(String code, Hub hub, String area, double powerKw) {
        this.code = code;
        this.hub = hub;
        this.area = area;
        this.powerKw = powerKw;
    }

    /** True if the port is in state AVAILABLE. */
    public boolean isAvailable() { return status == PortStatus.AVAILABLE; }

    /** True if the port is AVAILABLE and not reserved for an ASSIGNED request. */
    public boolean isFree() { return status == PortStatus.AVAILABLE && reservedRequestId == null; }

    public void reserveFor(Long requestId) {
        if (!isFree()) throw new IllegalStateException("Cổng " + code + " không còn trống");
        this.reservedRequestId = requestId;
    }

    public void clearReservation() { this.reservedRequestId = null; }

    /** AVAILABLE -> CHARGING when the vehicle of the assigned request is plugged in. */
    public void startCharging() {
        if (status != PortStatus.AVAILABLE) throw new IllegalStateException("Cổng " + code + " đang " + status);
        this.status = PortStatus.CHARGING;
    }

    /** CHARGING -> AVAILABLE when charging is finished (Ready) or the vehicle is unplugged. */
    public void release() {
        if (status == PortStatus.CHARGING) this.status = PortStatus.AVAILABLE;
        this.reservedRequestId = null;
    }

    /** -> FIXING when the port is broken; the port is not removed from the system. */
    public void markFixing() {
        this.status = PortStatus.FIXING;
        this.reservedRequestId = null;
    }

    /** FIXING -> AVAILABLE after repair. */
    public void markRepaired() {
        if (status != PortStatus.FIXING) throw new IllegalStateException("Cổng " + code + " không ở trạng thái FIXING");
        this.status = PortStatus.AVAILABLE;
    }

    public void recordReading(Double voltage, Double current, Double temperature) {
        this.lastReadingAt = LocalDateTime.now();
        if (voltage != null) this.lastVoltage = voltage;
        if (current != null) this.lastCurrent = current;
        if (temperature != null) this.lastTemperature = temperature;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    @JsonIgnore
    public Hub getHub() { return hub; }
    public Long getHubId() { return hub.getId(); }
    public String getArea() { return area; }
    public double getPowerKw() { return powerKw; }
    public PortStatus getStatus() { return status; }
    public Long getReservedRequestId() { return reservedRequestId; }
    public LocalDateTime getLastReadingAt() { return lastReadingAt; }
    public Double getLastVoltage() { return lastVoltage; }
    public Double getLastCurrent() { return lastCurrent; }
    public Double getLastTemperature() { return lastTemperature; }
}

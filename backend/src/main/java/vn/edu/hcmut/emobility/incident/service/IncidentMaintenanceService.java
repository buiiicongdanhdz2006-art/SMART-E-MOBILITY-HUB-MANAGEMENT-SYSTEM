package vn.edu.hcmut.emobility.incident.service;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.common.api.IIncidentReport;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.api.IVehicleState;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.events.PortFaultEvent;
import vn.edu.hcmut.emobility.common.events.PortRepairedEvent;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.incident.dto.IncidentFilter;
import vn.edu.hcmut.emobility.incident.dto.IncidentReportRequest;
import vn.edu.hcmut.emobility.incident.entity.Incident;
import vn.edu.hcmut.emobility.incident.repository.IncidentRepository;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/** «control» IncidentMaintenanceService - F-09 (UC-F09-01 .. 05); provides IIncidentReport. */
@Service
public class IncidentMaintenanceService implements IIncidentReport {

    private final IncidentRepository incidents;
    private final HubRepository hubs;
    private final VehicleRepository vehicles;
    private final ChargingPortRepository ports;
    private final IVehicleState vehicleState;
    private final INotification notification;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentMaintenanceService(IncidentRepository incidents, HubRepository hubs, VehicleRepository vehicles,
                                      ChargingPortRepository ports, IVehicleState vehicleState, INotification notification,
                                      ApplicationEventPublisher eventPublisher) {
        this.incidents = incidents;
        this.hubs = hubs;
        this.vehicles = vehicles;
        this.ports = ports;
        this.vehicleState = vehicleState;
        this.notification = notification;
        this.eventPublisher = eventPublisher;
    }

    /** UC-F09-01: validates the report and creates an incident REPORTED. */
    @Transactional
    public Incident reportIncident(IncidentReportRequest req, Long reporterId) {
        List<String> errors = req.validate();
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        Incident i = create(req.type(), req.severity(), req.hubId(), req.vehicleId(), req.portId(), req.title().trim(),
                req.description().trim(), reporterId, false);
        notification.notifyOperators(AlertType.INCIDENT, "Sự cố mới #" + i.getId(), i.getTitle());
        return i;
    }

    /** UC-F09-01: incident created automatically when an abnormal condition is detected (IoT, dispatch...). */
    @Transactional
    public Incident autoDetectIncident(Long vehicleId, IncidentType type, String detail) {
        Vehicle v = vehicles.findById(vehicleId).orElseThrow(() -> ApiException.notFound("xe"));
        Incident i = create(type, SeverityLevel.HIGH, v.getHubId(), vehicleId, null,
                "Phát hiện tự động: " + type + " - xe " + v.getCode(), detail, null, true);
        notification.notifyOperators(AlertType.INCIDENT, "Sự cố tự động #" + i.getId(), i.getTitle());
        return i;
    }

    @Transactional(readOnly = true)
    public Incident getIncidentDetail(Long id) {
        return incidents.findById(id).orElseThrow(() -> ApiException.notFound("sự cố #" + id));
    }

    /** UC-F09-02 */
    @Transactional
    public Incident investigateIncident(Long id, Operator op) {
        Incident i = getIncidentDetail(id);
        i.investigate(op.getId());
        return incidents.save(i);
    }

    /** UC-F09-02: false alarm. */
    @Transactional
    public Incident markIncidentInvalid(Long id, Operator op, String reason) {
        Incident i = getIncidentDetail(id);
        i.markInvalid(op.getId(), reason);
        return incidents.save(i);
    }

    /** UC-F09-03: incident and vehicle -&gt; MAINTENANCE (port -&gt; FIXING), notifies the maintenance personnel. */
    @Transactional
    public Incident assignToMaintenance(Long id, Operator op) {
        Incident i = getIncidentDetail(id);
        i.assignToMaintenance(op.getId());
        if (i.getVehicle() != null) {
            if (i.getVehicle().getStatus() == VehicleStatus.MAINTENANCE) {
                i.logTransition(i.getStatus(), i.getStatus(), op.getId(), "Cảnh báo: xe đã ở trạng thái bảo trì");
            } else {
                vehicleState.forceStatus(i.getVehicleId(), VehicleStatus.MAINTENANCE);
            }
        }
        if (i.getPort() != null && i.getPort().getStatus() != PortStatus.FIXING) {
            eventPublisher.publishEvent(new PortFaultEvent(i.getPortId(), i.getTitle()));
        }
        notification.notifyMaintenanceStaff("Sự cố #" + i.getId() + ": " + i.getTitle(),
                i.getDescription() + (i.getVehicleCode() != null ? " | Xe: " + i.getVehicleCode() : "")
                        + (i.getPortCode() != null ? " | Cổng: " + i.getPortCode() : "")
                        + (i.getHubName() != null ? " | Trạm: " + i.getHubName() : ""));
        return incidents.save(i);
    }

    /** UC-F09-04: resolves the incident, vehicle back to AVAILABLE, port back to AVAILABLE. */
    @Transactional
    public Incident resolveIncident(Long id, Operator op, String note) {
        Incident i = getIncidentDetail(id);
        i.resolve(op.getId(), note);
        if (i.getVehicle() != null && i.getVehicle().getStatus() == VehicleStatus.MAINTENANCE) {
            vehicleState.changeStatus(i.getVehicleId(), VehicleStatus.AVAILABLE);
        }
        if (i.getPort() != null && i.getPort().getStatus() == PortStatus.FIXING) {
            eventPublisher.publishEvent(new PortRepairedEvent(i.getPortId()));
        }
        return incidents.save(i);
    }

    /** UC-F09-05 */
    @Transactional(readOnly = true)
    public List<Incident> getIncidentHistory(IncidentFilter filter) {
        return incidents.findByFilter(filter);
    }

    // ---- IIncidentReport -----------------------------------------------------------------

    @Override
    @Transactional
    public Long reportIncident(IncidentType type, SeverityLevel severity, Long hubId, Long vehicleId, Long portId,
                               String title, String description, Long reporterId) {
        Incident i = create(type, severity, hubId, vehicleId, portId, title, description, reporterId, reporterId == null);
        notification.notifyOperators(AlertType.INCIDENT, "Sự cố #" + i.getId(), title);
        return i.getId();
    }

    @Override
    public boolean hasOpenIncident(IncidentType type, Long vehicleId, Long portId) {
        return incidents.findAllByOrderByCreatedAtDesc().stream()
                .filter(Incident::isOpen)
                .filter(i -> i.getType() == type)
                .anyMatch(i -> (vehicleId != null && vehicleId.equals(i.getVehicleId())) || (portId != null && portId.equals(i.getPortId())));
    }

    private Incident create(IncidentType type, SeverityLevel severity, Long hubId, Long vehicleId, Long portId,
                            String title, String description, Long reporterId, boolean auto) {
        Vehicle v = vehicleId == null ? null : vehicles.findById(vehicleId).orElseThrow(() -> ApiException.notFound("xe #" + vehicleId));
        ChargingPort p = portId == null ? null : ports.findById(portId).orElseThrow(() -> ApiException.notFound("cổng #" + portId));
        Long effectiveHub = hubId != null ? hubId : (p != null ? p.getHubId() : (v != null ? v.getHubId() : null));
        Hub h = effectiveHub == null ? null : hubs.findById(effectiveHub).orElse(null);
        return incidents.save(new Incident(type, severity, h, v, p, title, description, reporterId, auto));
    }
}

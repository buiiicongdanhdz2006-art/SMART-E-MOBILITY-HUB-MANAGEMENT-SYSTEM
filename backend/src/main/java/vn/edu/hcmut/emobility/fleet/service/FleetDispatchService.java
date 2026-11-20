package vn.edu.hcmut.emobility.fleet.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.api.IIncidentReport;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.api.IVehicleState;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.DispatchStatus;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.websocket.LiveUpdatePublisher;
import vn.edu.hcmut.emobility.fleet.dto.DispatchRequest;
import vn.edu.hcmut.emobility.fleet.dto.FleetFilter;
import vn.edu.hcmut.emobility.fleet.dto.FleetStatistics;
import vn.edu.hcmut.emobility.fleet.dto.HubImbalance;
import vn.edu.hcmut.emobility.fleet.dto.RedistributionSuggestion;
import vn.edu.hcmut.emobility.fleet.entity.DispatchOrder;
import vn.edu.hcmut.emobility.fleet.repository.DispatchOrderRepository;
import vn.edu.hcmut.emobility.hub.dto.Location;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.service.HubService;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/** «control» FleetDispatchService - F-08 Vehicle monitoring &amp; dispatching (UC-F08-01 .. 05). */
@Service
public class FleetDispatchService {

    /** A vehicle closer than this to the destination Hub is considered arrived. */
    public static final double ARRIVAL_RADIUS_KM = 0.15;

    private final VehicleRepository vehicles;
    private final DispatchOrderRepository orders;
    private final IHubQuery hubQuery;
    private final IVehicleState vehicleState;
    private final IIncidentReport incidentReport;
    private final INotification notification;
    private final ImbalanceDetector detector;
    private final RedistributionRecommender recommender;
    private final LiveUpdatePublisher live;

    public FleetDispatchService(VehicleRepository vehicles, DispatchOrderRepository orders, IHubQuery hubQuery,
                                IVehicleState vehicleState, IIncidentReport incidentReport, INotification notification,
                                ImbalanceDetector detector, RedistributionRecommender recommender, LiveUpdatePublisher live) {
        this.vehicles = vehicles;
        this.orders = orders;
        this.hubQuery = hubQuery;
        this.vehicleState = vehicleState;
        this.incidentReport = incidentReport;
        this.notification = notification;
        this.detector = detector;
        this.recommender = recommender;
        this.live = live;
    }

    /** UC-F08-01: fleet statistics by the selected filter. */
    @Transactional(readOnly = true)
    public FleetStatistics getFleetStatistics(FleetFilter filter) {
        return FleetStatistics.calculate(findByFilter(filter));
    }

    /** VehicleRepository.findByFilter(f). */
    @Transactional(readOnly = true)
    public List<Vehicle> findByFilter(FleetFilter filter) {
        List<Vehicle> all = vehicles.findAllByOrderByCode();
        return filter == null || filter.isEmpty() ? all : all.stream().filter(filter::matches).toList();
    }

    /** UC-F08-02: loads all Hubs and returns those with shortage or surplus. */
    @Transactional(readOnly = true)
    public List<HubImbalance> detectImbalance() {
        return detector.detect(hubQuery.getAllHubs());
    }

    /** UC-F08-03: redistribution suggestions for the detected imbalances. */
    @Transactional(readOnly = true)
    public List<RedistributionSuggestion> recommendRedistribution(List<HubImbalance> imbalances) {
        return recommender.recommend(imbalances);
    }

    /** UC-F08-04: validates, checks the source Hub has enough vehicles, creates the order, vehicles -&gt; IN_TRANSIT. */
    @Transactional
    public DispatchOrder createDispatchOrder(DispatchRequest req, Operator op) {
        List<String> errors = req.validate();
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        Hub from = hubQuery.getHub(req.fromHubId());
        Hub to = hubQuery.getHub(req.toHubId());
        if (!to.isActive()) throw ApiException.conflict("Trạm đích đang ngưng hoạt động");
        if (!from.hasEnoughAvailable(req.quantity())) {
            throw ApiException.conflict("Trạm " + from.getName() + " chỉ còn " + from.countAvailable() + " xe sẵn sàng");
        }
        List<Vehicle> selected = new ArrayList<>();
        if (req.vehicleIds() != null && !req.vehicleIds().isEmpty()) {
            for (Long id : req.vehicleIds()) {
                Vehicle v = vehicles.lockById(id).orElseThrow(() -> ApiException.notFound("xe #" + id));
                if (!v.isAvailable() || !from.getId().equals(v.getHubId())) {
                    throw ApiException.conflict("Xe " + v.getCode() + " không sẵn sàng tại trạm nguồn");
                }
                selected.add(v);
            }
        } else {
            for (Vehicle v : vehicles.findAvailableByHub(from.getId())) {
                if (selected.size() == req.quantity()) break;
                selected.add(vehicles.lockById(v.getId()).orElseThrow());
            }
        }
        DispatchOrder order = new DispatchOrder(from, to, selected, op.getId(), req.plannedArrival(), req.note());
        order.start();
        orders.save(order);
        notification.notifyOperators(AlertType.GENERAL, "Lệnh điều phối #" + order.getId(),
                "Điều " + selected.size() + " xe từ " + from.getName() + " đến " + to.getName() + ".");
        publish(order);
        return order;
    }

    /** UC-F08-05: ongoing dispatch orders with their progress. */
    @Transactional(readOnly = true)
    public List<DispatchOrder> getActiveDispatchOrders() { return orders.findActive(); }

    /** UC-F08-05: updates the IN_TRANSIT vehicles of an order from their last position (auto-arrival). */
    @Transactional
    public DispatchOrder refreshDispatchProgress(Long orderId) {
        DispatchOrder order = order(orderId);
        if (order.getStatus() != DispatchStatus.IN_PROGRESS) return order;
        Hub to = order.getToHub();
        for (Vehicle v : order.getVehicles()) {
            if (!order.isPending(v) || v.getLatitude() == null || v.getLongitude() == null) continue;
            double km = HubService.distance(new Location(v.getLatitude(), v.getLongitude()), new Location(to.getLatitude(), to.getLongitude()));
            if (km <= ARRIVAL_RADIUS_KM) order.markVehicleArrived(v);
        }
        completeIfDone(order);
        publish(order);
        return order;
    }

    /** UC-F08-05: confirms one arrival; completes the order when every vehicle is done. */
    @Transactional
    public DispatchOrder confirmArrival(Long orderId, Long vehicleId) {
        DispatchOrder order = order(orderId);
        if (order.getStatus() != DispatchStatus.IN_PROGRESS) throw ApiException.conflict("Lệnh điều phối không còn hoạt động");
        order.markVehicleArrived(vehicleState.getVehicle(vehicleId));
        completeIfDone(order);
        publish(order);
        return order;
    }

    /** UC-F08-05: records an incident of a vehicle in transit and sends it to F-09. */
    @Transactional
    public Long reportDispatchIncident(Long orderId, Long vehicleId, Map<String, String> report, Operator op) {
        DispatchOrder order = order(orderId);
        Vehicle v = vehicleState.getVehicle(vehicleId);
        String description = report.getOrDefault("description", "").trim();
        if (description.isEmpty()) throw ApiException.badRequest("Mô tả sự cố là bắt buộc");
        IncidentType type;
        try {
            type = IncidentType.valueOf(report.getOrDefault("type", "VEHICLE_FAULT"));
        } catch (IllegalArgumentException e) {
            type = IncidentType.OTHER;
        }
        Long incidentId = incidentReport.reportIncident(type, SeverityLevel.HIGH, order.getToHubId(), vehicleId, null,
                "Sự cố khi điều phối xe " + v.getCode() + " (lệnh #" + orderId + ")", description, op.getId());
        order.flagIncident(v, incidentId);
        v.forceStatus(VehicleStatus.MAINTENANCE);
        completeIfDone(order);
        publish(order);
        return incidentId;
    }

    /** UC-F08-05: manually corrects a wrong vehicle status. */
    @Transactional
    public Vehicle correctVehicleStatus(Long vehicleId, VehicleStatus newStatus, Operator op) {
        vehicleState.forceStatus(vehicleId, newStatus);
        return vehicleState.getVehicle(vehicleId);
    }

    private void completeIfDone(DispatchOrder order) {
        if (order.getStatus() == DispatchStatus.IN_PROGRESS && order.isAllDone()) {
            order.complete();
            notification.notifyOperators(AlertType.GENERAL, "Hoàn tất điều phối #" + order.getId(),
                    order.getArrivedVehicleIds().size() + "/" + order.getQuantity() + " xe đã đến " + order.getToHubName() + ".");
        }
    }

    private DispatchOrder order(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("lệnh điều phối #" + id));
    }

    private void publish(DispatchOrder o) {
        live.publish("dispatch", Map.of("orderId", o.getId(), "status", o.getStatus().name(), "progress", o.getProgress()));
    }
}

package vn.edu.hcmut.emobility.fleet.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.common.security.Permissions;
import vn.edu.hcmut.emobility.fleet.dto.DispatchRequest;
import vn.edu.hcmut.emobility.fleet.dto.FleetFilter;
import vn.edu.hcmut.emobility.fleet.dto.FleetStatistics;
import vn.edu.hcmut.emobility.fleet.dto.HubImbalance;
import vn.edu.hcmut.emobility.fleet.dto.RedistributionSuggestion;
import vn.edu.hcmut.emobility.fleet.entity.DispatchOrder;
import vn.edu.hcmut.emobility.fleet.service.FleetDispatchService;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** REST boundary of F-08 (Operator). */
@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    private final FleetDispatchService service;

    public FleetController(FleetDispatchService service) {
        this.service = service;
    }

    @GetMapping("/statistics")
    public FleetStatistics statistics(HttpServletRequest request, @RequestParam(required = false) Long hubId,
                                      @RequestParam(required = false) VehicleType type, @RequestParam(required = false) VehicleStatus status) {
        CurrentAccount.requireOperator(request, Permissions.VIEW_FLEET);
        return service.getFleetStatistics(new FleetFilter(hubId, type, status));
    }

    @GetMapping("/vehicles")
    public List<Vehicle> vehicles(HttpServletRequest request, @RequestParam(required = false) Long hubId,
                                  @RequestParam(required = false) VehicleType type, @RequestParam(required = false) VehicleStatus status) {
        CurrentAccount.requireOperator(request, Permissions.VIEW_FLEET);
        return service.findByFilter(new FleetFilter(hubId, type, status));
    }

    @GetMapping("/imbalances")
    public List<HubImbalance> imbalances(HttpServletRequest request) {
        CurrentAccount.requireOperator(request, Permissions.VIEW_FLEET);
        return service.detectImbalance();
    }

    @GetMapping("/recommendations")
    public List<RedistributionSuggestion> recommendations(HttpServletRequest request) {
        CurrentAccount.requireOperator(request, Permissions.VIEW_FLEET);
        return service.recommendRedistribution(service.detectImbalance());
    }

    @PostMapping("/dispatch-orders")
    public DispatchOrder createDispatchOrder(HttpServletRequest request, @RequestBody DispatchRequest body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.DISPATCH);
        return service.createDispatchOrder(body, op);
    }

    @GetMapping("/dispatch-orders/active")
    public List<DispatchOrder> active(HttpServletRequest request) {
        CurrentAccount.requireOperator(request, Permissions.VIEW_FLEET);
        return service.getActiveDispatchOrders();
    }

    @PostMapping("/dispatch-orders/{orderId}/refresh")
    public DispatchOrder refresh(HttpServletRequest request, @PathVariable Long orderId) {
        CurrentAccount.requireOperator(request, Permissions.DISPATCH);
        return service.refreshDispatchProgress(orderId);
    }

    @PostMapping("/dispatch-orders/{orderId}/arrivals/{vehicleId}")
    public DispatchOrder confirmArrival(HttpServletRequest request, @PathVariable Long orderId, @PathVariable Long vehicleId) {
        CurrentAccount.requireOperator(request, Permissions.DISPATCH);
        return service.confirmArrival(orderId, vehicleId);
    }

    @PostMapping("/dispatch-orders/{orderId}/incidents")
    public Map<String, Object> reportIncident(HttpServletRequest request, @PathVariable Long orderId, @RequestBody Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.DISPATCH);
        if (body.get("vehicleId") == null) throw ApiException.badRequest("Chưa chọn xe");
        Long incidentId = service.reportDispatchIncident(orderId, Long.valueOf(body.get("vehicleId")), body, op);
        return Map.of("incidentId", incidentId);
    }

    @PutMapping("/vehicles/{vehicleId}/status")
    public Vehicle correctStatus(HttpServletRequest request, @PathVariable Long vehicleId, @RequestBody Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.DISPATCH);
        VehicleStatus st;
        try {
            st = VehicleStatus.valueOf(body.getOrDefault("status", ""));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Trạng thái không hợp lệ");
        }
        return service.correctVehicleStatus(vehicleId, st, op);
    }
}

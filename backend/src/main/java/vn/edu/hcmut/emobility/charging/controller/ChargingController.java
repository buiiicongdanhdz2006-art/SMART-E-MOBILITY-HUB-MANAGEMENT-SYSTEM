package vn.edu.hcmut.emobility.charging.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.charging.dto.ChargingRequestForm;
import vn.edu.hcmut.emobility.charging.dto.ChargingRequestView;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.service.ChargingService;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;

/** «boundary» ChargingController - F-06 for User (and Operator for shared vehicles). */
@RestController
@RequestMapping("/api/charging")
public class ChargingController {

    private final ChargingService chargingService;

    public ChargingController(ChargingService chargingService) {
        this.chargingService = chargingService;
    }

    /** UC-F06-01: ports of a Hub with their status (public). */
    @GetMapping("/hubs/{hubId}/ports")
    public List<ChargingPort> getPorts(@PathVariable Long hubId) {
        return chargingService.getPortStatus(hubId);
    }

    /** UC-F06-01: User reports a broken port. */
    @PostMapping("/ports/{portId}/fault")
    public Map<String, Object> reportPortFault(HttpServletRequest request, @PathVariable Long portId, @RequestBody Map<String, String> body) {
        Account a = CurrentAccount.require(request);
        Long incidentId = chargingService.reportPortFault(portId, body.get("note"), a);
        return Map.of("incidentId", incidentId, "message", "Đã ghi nhận lỗi, cổng chuyển sang trạng thái FIXING");
    }

    /** UC-F06-02 / 03: charging registration form. */
    @PostMapping("/requests")
    public ChargingRequestView submitRequest(HttpServletRequest request, @RequestBody ChargingRequestForm form) {
        return chargingService.createRequest(form, CurrentAccount.require(request));
    }

    /** UC-F06-05: requests of the signed-in account. */
    @GetMapping("/requests/my")
    public List<ChargingRequestView> getMyRequests(HttpServletRequest request) {
        return chargingService.getRequestsByUser(CurrentAccount.require(request).getId());
    }

    /** UC-F06-04: battery level and charging status (polled, or pushed through WebSocket /ws). */
    @GetMapping("/requests/{requestId}/status")
    public ChargingRequestView trackCharging(HttpServletRequest request, @PathVariable Long requestId) {
        return chargingService.getChargingStatus(requestId, CurrentAccount.require(request));
    }

    /** UC-F06-04: stop charging early. */
    @PostMapping("/requests/{requestId}/stop")
    public ChargingRequestView stopCharging(HttpServletRequest request, @PathVariable Long requestId) {
        return chargingService.stopCharging(requestId, CurrentAccount.require(request));
    }

    /** UC-F06-05: cancel a request that has not started charging. */
    @PostMapping("/requests/{requestId}/cancel")
    public ChargingRequestView cancelRequest(HttpServletRequest request, @PathVariable Long requestId) {
        return chargingService.cancelRequest(requestId, CurrentAccount.require(request));
    }

    /** Waiting queue of a Hub in processing order. */
    @GetMapping("/hubs/{hubId}/queue")
    public List<ChargingRequestView> getQueue(HttpServletRequest request, @PathVariable Long hubId) {
        CurrentAccount.require(request);
        return chargingService.getQueue(hubId);
    }

    /** ASSIGNED and CHARGING requests of a Hub. */
    @GetMapping("/hubs/{hubId}/active")
    public List<ChargingRequestView> getActive(HttpServletRequest request, @PathVariable Long hubId) {
        CurrentAccount.require(request);
        return chargingService.getActiveByHub(hubId);
    }
}

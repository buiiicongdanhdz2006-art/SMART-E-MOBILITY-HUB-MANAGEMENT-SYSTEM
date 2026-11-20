package vn.edu.hcmut.emobility.charging.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.charging.dto.ScheduleParams;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingSchedule;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.ChargingRequestRepository;
import vn.edu.hcmut.emobility.charging.repository.ScheduleRepository;
import vn.edu.hcmut.emobility.charging.service.SmartScheduler;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.common.security.Permissions;

/** «boundary» ScheduleController - F-07 for the Operator (UC-F07-01 .. 03). */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final SmartScheduler scheduler;
    private final ChargingRequestRepository requests;
    private final ChargingPortRepository ports;
    private final ScheduleRepository schedules;

    public ScheduleController(SmartScheduler scheduler, ChargingRequestRepository requests,
                              ChargingPortRepository ports, ScheduleRepository schedules) {
        this.scheduler = scheduler;
        this.requests = requests;
        this.ports = ports;
        this.schedules = schedules;
    }

    /** UC-F07-01: checks the Operator permission and the time window, then schedules the pending requests. */
    @PostMapping("/generate")
    @Transactional
    public ChargingSchedule generateSchedule(HttpServletRequest request, @RequestBody ScheduleParams params) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_CHARGING);
        List<String> errors = new ArrayList<>();
        if (params == null || params.hubId() == null) errors.add("Chưa chọn trạm");
        LocalDateTime now = LocalDateTime.now();
        if (params != null && params.windowStart() != null && params.windowEnd() != null && !params.windowStart().isBefore(params.windowEnd())) {
            errors.add("Giờ bắt đầu phải trước giờ kết thúc");
        }
        if (params != null && params.windowEnd() != null && params.windowEnd().isBefore(now)) errors.add("Khung giờ đã qua");
        if (params != null && params.lowBatteryThreshold() != null && (params.lowBatteryThreshold() < 1 || params.lowBatteryThreshold() > 99)) {
            errors.add("Ngưỡng pin thấp phải trong khoảng 1-99");
        }
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        return scheduler.schedule(requests.findPendingByHub(params.hubId()).stream()
                .filter(r -> r.getStatus() != vn.edu.hcmut.emobility.common.enums.RequestStatus.ASSIGNED).toList(), params, op.getId());
    }

    /** UC-F07-02: Priority Override - a reason is mandatory. */
    @PostMapping("/requests/{requestId}/urgent")
    public ChargingSchedule markUrgent(HttpServletRequest request, @PathVariable Long requestId, @RequestBody Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_CHARGING);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Bắt buộc nhập lý do khẩn cấp");
        return scheduler.markUrgent(requestId, reason, op.getId());
    }

    /** UC-F07-03: recalculates the schedule (e.g. when the Hub is overloaded). */
    @PostMapping("/hubs/{hubId}/reschedule")
    public ChargingSchedule reschedule(HttpServletRequest request, @PathVariable Long hubId) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_CHARGING);
        return scheduler.reschedule(hubId, op.getId());
    }

    @GetMapping("/hubs/{hubId}/latest")
    public ChargingSchedule latest(HttpServletRequest request, @PathVariable Long hubId) {
        CurrentAccount.requireOperator(request, null);
        return schedules.findFirstByHub_IdOrderByCreatedAtDesc(hubId).orElseThrow(() -> ApiException.notFound("lịch sạc của trạm"));
    }

    /** The Operator marks a port FIXING (manual) or repaired (FIXING -&gt; AVAILABLE). */
    @PostMapping("/ports/{portId}/status")
    @Transactional
    public ChargingPort setPortStatus(HttpServletRequest request, @PathVariable Long portId, @RequestBody Map<String, String> body) {
        CurrentAccount.requireOperator(request, Permissions.MANAGE_CHARGING);
        ChargingPort port = ports.findById(portId).orElseThrow(() -> ApiException.notFound("cổng sạc"));
        PortStatus status;
        try {
            status = PortStatus.valueOf(body.getOrDefault("status", ""));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Trạng thái phải là FIXING hoặc AVAILABLE");
        }
        if (status == PortStatus.CHARGING) throw ApiException.badRequest("Trạng thái CHARGING chỉ do cảm biến cập nhật");
        scheduler.portStatusChanged(port, status);
        return port;
    }
}

package vn.edu.hcmut.emobility.simulation.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.enums.ScenarioType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.common.security.Permissions;
import vn.edu.hcmut.emobility.simulation.entity.HubState;
import vn.edu.hcmut.emobility.simulation.entity.ImpactMetrics;
import vn.edu.hcmut.emobility.simulation.entity.Scenario;
import vn.edu.hcmut.emobility.simulation.entity.SimulationResult;
import vn.edu.hcmut.emobility.simulation.entity.SuggestedAction;
import vn.edu.hcmut.emobility.simulation.entity.TimeWindow;
import vn.edu.hcmut.emobility.simulation.entity.ValidationResult;
import vn.edu.hcmut.emobility.simulation.repository.ScenarioRepository;
import vn.edu.hcmut.emobility.simulation.service.HubDatabase;
import vn.edu.hcmut.emobility.simulation.service.SimulationEngine;

/**
 * «control» SimulationController (F-10). The SimulationUI boundary is the "Mô phỏng" page of the web client,
 * which calls these endpoints.
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);
    private static final Set<String> RESERVED = Set.of("type", "hubId", "windowStart", "windowEnd");

    private final SimulationEngine engine;
    private final HubDatabase hubDatabase;
    private final ScenarioRepository scenarios;
    private final IHubQuery hubQuery;

    public SimulationController(SimulationEngine engine, HubDatabase hubDatabase, ScenarioRepository scenarios, IHubQuery hubQuery) {
        this.engine = engine;
        this.hubDatabase = hubDatabase;
        this.scenarios = scenarios;
        this.hubQuery = hubQuery;
    }

    /**
     * Creates the Scenario, validates it, reads the hub state, runs the engine, generates suggestions and
     * returns the result to SimulationUI; the scenario is stored as lastScenario of the Operator.
     */
    @PostMapping("/scenarios")
    @Transactional
    public SimulationResult submitScenario(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.RUN_SIMULATION);
        if (!engine.isAvailable()) throw ApiException.unavailable("Engine mô phỏng đang bận, vui lòng thử lại");
        Scenario scenario = buildScenario(params, op.getId());
        ValidationResult validation = validateParameters(scenario);
        if (!validation.isValid()) throw ApiException.validation(validation.getErrors());
        scenarios.save(scenario);
        try {
            HubState state = hubDatabase.getCurrentHubState(scenario.getHubId(), scenario.getWindow());
            SimulationResult result = engine.runSimulation(scenario, state);
            result.addSuggestions(generateSuggestions(result.getMetrics(), result.getAfter(), scenario));
            scenario.recordResult(result.getMetrics().getOverallSeverity(), result.getMetrics().toSummary());
            return result;
        } catch (RuntimeException e) {
            return handleSimulationFailure(scenario, e);
        }
    }

    /** Copy of the last scenario of the Operator, to prefill the form on Rerun (F10-AF-03). */
    @GetMapping("/rerun")
    public Scenario prepareRerun(HttpServletRequest request) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.RUN_SIMULATION);
        return scenarios.findFirstByCreatedByOrderByCreatedAtDesc(op.getId())
                .map(Scenario::copy)
                .orElseThrow(() -> ApiException.notFound("kịch bản trước đó"));
    }

    @GetMapping("/scenarios")
    public List<Scenario> history(HttpServletRequest request) {
        CurrentAccount.requireOperator(request, Permissions.RUN_SIMULATION);
        return scenarios.findTop20ByOrderByCreatedAtDesc();
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return Map.of("available", engine.isAvailable()); }

    /** Validates scenario type, hub, time window and parameter value ranges (F10-NF-04). */
    ValidationResult validateParameters(Scenario s) {
        ValidationResult v = new ValidationResult();
        if (s.getType() == null) v.addError("Chọn loại kịch bản");
        if (s.getHubId() == null) v.addError("Chọn trạm");
        else {
            try { hubQuery.getHub(s.getHubId()); } catch (ApiException e) { v.addError("Trạm không tồn tại"); }
        }
        if (s.getWindow() == null || !s.getWindow().isValid()) v.addError("Khung thời gian không hợp lệ (bắt đầu phải trước kết thúc)");
        else if (s.getWindow().getDuration().compareTo(Duration.ofDays(7)) > 0) v.addError("Khung thời gian tối đa 7 ngày");
        if (s.getType() == null) return v;
        switch (s.getType()) {
            case HOURS_SETTING -> {
                Double open = s.getNumber("openHour");
                Double close = s.getNumber("closeHour");
                if (open == null || close == null) v.addError("Nhập giờ mở cửa và giờ đóng cửa");
                else {
                    if (open < 0 || open > 23) v.addError("Giờ mở cửa trong khoảng 0-23");
                    if (close < 1 || close > 24) v.addError("Giờ đóng cửa trong khoảng 1-24");
                    if (open >= close) v.addError("Giờ mở cửa phải trước giờ đóng cửa");
                }
            }
            case CAPACITY_LIMIT -> {
                Double a = s.getNumber("maxVehicles"), b = s.getNumber("maxPorts"), c = s.getNumber("maxSlots");
                if (a == null && b == null && c == null) v.addError("Nhập ít nhất một giới hạn: maxVehicles, maxPorts, maxSlots");
                for (Double x : new Double[]{a, b, c}) if (x != null && (x < 0 || x > 1000)) v.addError("Giới hạn công suất trong khoảng 0-1000");
            }
            case HARDWARE_FAILURE -> {
                Double p = s.getNumber("failedPorts"), q = s.getNumber("failedVehicles");
                if (p == null && q == null) v.addError("Nhập số cổng hỏng (failedPorts) hoặc số xe hỏng (failedVehicles)");
                if (p != null && p < 0) v.addError("Số cổng hỏng không được âm");
                if (q != null && q < 0) v.addError("Số xe hỏng không được âm");
            }
            case CONGESTION_SETTING -> {
                Double m = s.getNumber("demandMultiplier");
                if (m == null || m < 0.1 || m > 5) v.addError("Hệ số nhu cầu (demandMultiplier) trong khoảng 0.1-5");
            }
        }
        return v;
    }

    /** Suggested actions from the impact metrics, sorted by priority; advisory only (F10-POST-05). */
    List<SuggestedAction> generateSuggestions(ImpactMetrics m, HubState after, Scenario s) {
        List<SuggestedAction> list = new ArrayList<>();
        if (m.vehicleShortageAfter >= 1) {
            list.add(new SuggestedAction(m.vehicleShortageAfter >= 3 ? 90 : 60, "Điều phối thêm xe (F-08)",
                    String.format("Trạm thiếu khoảng %.0f xe/giờ ở giờ cao điểm. Tạo lệnh điều phối từ trạm dư xe.", Math.ceil(m.vehicleShortageAfter))));
        }
        if (m.portLoadAfter >= 0.85) {
            int extra = (int) Math.ceil(after.chargingDemandPerHour / Math.max(0.1, after.portThroughputPerHour) - after.workingPorts);
            list.add(new SuggestedAction(m.portLoadAfter >= 1 ? 95 : 70, "Tăng năng lực sạc",
                    "Tải cổng sạc " + Math.round(m.portLoadAfter * 100) + "%. Cần thêm khoảng " + Math.max(1, extra)
                            + " cổng hoạt động hoặc chuyển bớt yêu cầu sang trạm lân cận."));
        }
        if (m.avgWaitMinutesAfter >= 15) {
            list.add(new SuggestedAction(m.avgWaitMinutesAfter >= 30 ? 80 : 50, "Ưu tiên lịch sạc thông minh (F-07)",
                    "Thời gian chờ trung bình " + Math.round(m.avgWaitMinutesAfter) + " phút. Chạy lại lịch sạc và ưu tiên xe sắp sử dụng."));
        }
        if (s.getType() == ScenarioType.HARDWARE_FAILURE) {
            list.add(new SuggestedAction(85, "Sửa chữa thiết bị hỏng (F-09)", "Tạo sự cố và chuyển bảo trì ngay cho các cổng / xe hỏng."));
        }
        if (s.getType() == ScenarioType.HOURS_SETTING && m.unmetChargingAfter > m.unmetChargingBefore) {
            list.add(new SuggestedAction(65, "Xem lại giờ hoạt động",
                    String.format("Giờ hoạt động mới làm tăng nhu cầu sạc không đáp ứng từ %.1f lên %.1f lượt/ngày.", m.unmetChargingBefore, m.unmetChargingAfter)));
        }
        if (m.unmetChargingAfter > 0) {
            list.add(new SuggestedAction(55, "Gửi cảnh báo cho sinh viên (F-11)", "Thông báo trước khả năng phải chờ sạc và gợi ý trạm khác."));
        }
        if (list.isEmpty()) list.add(new SuggestedAction(10, "Giữ nguyên vận hành", "Kịch bản không gây ảnh hưởng đáng kể."));
        list.sort(null);
        return list;
    }

    /** Logs the error with HubDatabase.logError() and returns a failure result for SimulationUI (F10-AF-02). */
    SimulationResult handleSimulationFailure(Scenario scenario, Exception e) {
        log.warn("Simulation failed: {}", e.getMessage());
        hubDatabase.logError("SimulationEngine", e.getMessage());
        scenario.recordResult(null, "Thất bại: " + e.getMessage());
        return SimulationResult.failure(scenario, "Mô phỏng thất bại: " + e.getMessage() + ". Hãy chỉnh lại kịch bản và chạy lại.");
    }

    private Scenario buildScenario(Map<String, Object> params, Long operatorId) {
        ScenarioType type = null;
        try {
            if (params.get("type") != null) type = ScenarioType.valueOf(params.get("type").toString());
        } catch (IllegalArgumentException ignored) {
            // reported by validateParameters
        }
        Long hubId = null;
        try {
            if (params.get("hubId") != null) hubId = Long.valueOf(params.get("hubId").toString());
        } catch (NumberFormatException ignored) {
            // reported by validateParameters
        }
        LocalDateTime start = parse(params.get("windowStart"));
        LocalDateTime end = parse(params.get("windowEnd"));
        if (start == null) start = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        if (end == null) end = start.plusHours(1);
        Scenario s = new Scenario(type, hubId, new TimeWindow(start, end), operatorId);
        params.forEach((k, v) -> {
            if (!RESERVED.contains(k) && v != null && !v.toString().isBlank()) s.setParameter(k, v);
        });
        return s;
    }

    private static LocalDateTime parse(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return LocalDateTime.parse(o.toString()); } catch (Exception e) { return null; }
    }
}

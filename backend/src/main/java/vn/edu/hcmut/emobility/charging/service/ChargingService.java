package vn.edu.hcmut.emobility.charging.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.charging.dto.ChargingRequestForm;
import vn.edu.hcmut.emobility.charging.dto.ChargingRequestView;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.entity.ChargingSession;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.ChargingRequestRepository;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.api.IIncidentReport;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.api.IVehicleState;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;
import vn.edu.hcmut.emobility.parking.repository.PersonalVehicleRepository;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** «control» ChargingService - F-06 Charging management (UC-F06-01 .. 05). */
@Service
public class ChargingService {

    private static final Logger log = LoggerFactory.getLogger(ChargingService.class);
    public static final int MAX_DURATION_MINUTES = 600;

    private final ChargingRequestRepository requests;
    private final ChargingPortRepository ports;
    private final PersonalVehicleRepository personalVehicles;
    private final IHubQuery hubQuery;
    private final IVehicleState vehicleState;
    private final IIncidentReport incidentReport;
    private final INotification notification;
    private final SmartScheduler scheduler;
    private final IChargingEvents events;
    private final int lostTimeoutSeconds;
    private final double overheatCelsius;

    public ChargingService(ChargingRequestRepository requests, ChargingPortRepository ports,
                           PersonalVehicleRepository personalVehicles, IHubQuery hubQuery, IVehicleState vehicleState,
                           IIncidentReport incidentReport, INotification notification, SmartScheduler scheduler,
                           @Value("${iot.lost-timeout-seconds:60}") int lostTimeoutSeconds,
                           @Value("${iot.overheat-celsius:55}") double overheatCelsius) {
        this.requests = requests;
        this.ports = ports;
        this.personalVehicles = personalVehicles;
        this.hubQuery = hubQuery;
        this.vehicleState = vehicleState;
        this.incidentReport = incidentReport;
        this.notification = notification;
        this.scheduler = scheduler;
        this.events = scheduler;
        this.lostTimeoutSeconds = lostTimeoutSeconds;
        this.overheatCelsius = overheatCelsius;
    }

    // ---- UC-F06-01 ---------------------------------------------------------------------------

    /** Charging ports of the Hub with their status (Available, Charging, Fixing). */
    @Transactional(readOnly = true)
    public List<ChargingPort> getPortStatus(Long hubId) {
        hubQuery.getHub(hubId);
        return ports.findByHub_IdOrderByCode(hubId);
    }

    /** Forwards the fault to incident reporting (F-09.1), sets the port to FIXING and warns the Operators. */
    @Transactional
    public Long reportPortFault(Long portId, String note, Account reporter) {
        ChargingPort port = port(portId);
        if (note == null || note.isBlank()) throw ApiException.badRequest("Vui lòng mô tả lỗi của cổng sạc");
        Long incidentId = incidentReport.reportIncident(IncidentType.PORT_FAULT, SeverityLevel.HIGH, port.getHubId(), null,
                port.getId(), "Cổng sạc " + port.getCode() + " báo lỗi", note.trim(), reporter == null ? null : reporter.getId());
        events.portStatusChanged(port, PortStatus.FIXING);
        notification.notifyOperators(AlertType.PORT_FAULT, "Cổng sạc hỏng",
                "Cổng " + port.getCode() + " được báo lỗi: " + note.trim() + " (sự cố #" + incidentId + ").");
        return incidentId;
    }

    // ---- UC-F06-02 / 03 ----------------------------------------------------------------------

    /** Checks the form, saves the request, then assigns a free port or puts the request in the waiting queue. */
    @Transactional
    public ChargingRequestView createRequest(ChargingRequestForm dto, Account requester) {
        List<String> errors = validateRequest(dto);
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        Hub hub = hubQuery.getHub(dto.hubId());
        if (!hub.isActive()) throw ApiException.conflict("Trạm đang tạm ngưng hoạt động");
        if (hub.getPorts(dto.area()).isEmpty()) throw ApiException.badRequest("Khu vực " + dto.area() + " không có cổng sạc");

        Vehicle vehicle = null;
        PersonalVehicle personal = null;
        int battery;
        LocalDateTime nextUsage = dto.nextUsageTime();
        if (dto.personalVehicleId() != null) {
            if (!(requester instanceof User)) throw ApiException.forbidden("Chỉ sinh viên mới sạc xe cá nhân");
            personal = personalVehicles.findById(dto.personalVehicleId()).orElseThrow(() -> ApiException.notFound("xe cá nhân"));
            if (!personal.getOwnerId().equals(requester.getId())) throw ApiException.forbidden("Xe không thuộc về bạn");
            battery = dto.batteryLevel();
        } else {
            if (!(requester instanceof Operator)) throw ApiException.forbidden("Chỉ Operator mới đăng ký sạc xe dùng chung");
            vehicle = vehicleState.getVehicle(dto.vehicleId());
            if (vehicle.getHubId() == null || !vehicle.getHubId().equals(hub.getId())) throw ApiException.conflict("Xe không ở trạm này");
            if (vehicle.getStatus() != VehicleStatus.AVAILABLE) throw ApiException.conflict("Xe đang " + vehicle.getStatus());
            battery = dto.batteryLevel() == null ? vehicle.getBatteryLevel() : dto.batteryLevel();
            if (nextUsage == null) nextUsage = vehicle.getNextUsageTime();
        }
        String ref = vehicle != null ? vehicle.getCode() : personal.getPlate();
        boolean duplicate = requests.findByStatus(RequestStatus.WAITING).stream().anyMatch(r -> ref.equalsIgnoreCase(r.getVehicleRef()))
                || requests.findByStatus(RequestStatus.ASSIGNED).stream().anyMatch(r -> ref.equalsIgnoreCase(r.getVehicleRef()))
                || requests.findByStatus(RequestStatus.CHARGING).stream().anyMatch(r -> ref.equalsIgnoreCase(r.getVehicleRef()));
        if (duplicate) throw ApiException.conflict("Xe " + ref + " đã có yêu cầu sạc đang hoạt động");

        LocalDateTime start = dto.startTime() == null ? LocalDateTime.now() : dto.startTime();
        int target = dto.targetBattery() == null ? 100 : dto.targetBattery();
        ChargingRequest r = new ChargingRequest(requester, hub, blankToNull(dto.area()), vehicle, personal, battery, target,
                nextUsage, start, dto.expectedDuration());
        r.setPriority(scheduler.calculatePriority(r));
        requests.save(r);

        WaitingQueue queue = scheduler.queueOf(hub.getId());
        boolean queueEmptyForArea = queue.getAll().stream()
                .noneMatch(q -> r.getArea() == null || q.getArea() == null || r.getArea().equalsIgnoreCase(q.getArea()));
        ChargingPort port = queueEmptyForArea && isReady(r) ? findAvailablePort(hub.getId(), r.getArea(), start, r.getExpectedDuration()) : null;
        Integer position = null;
        if (port != null) {
            assignPort(r, port);
        } else {
            position = joinWaitingQueue(r);
        }
        events.onNewRequest(r);
        if (r.getStatus() == RequestStatus.WAITING) position = queue.getPosition(r);
        return ChargingRequestView.of(r, position);
    }

    /** Required: vehicle, Hub, area (optional), start time, expected duration; returns the errors. */
    public static List<String> validateRequest(ChargingRequestForm dto) {
        List<String> errors = new ArrayList<>();
        if (dto == null) return List.of("Thiếu thông tin đăng ký sạc");
        if (dto.hubId() == null) errors.add("Chưa chọn trạm sạc");
        if ((dto.vehicleId() == null) == (dto.personalVehicleId() == null)) errors.add("Chọn đúng một xe cần sạc");
        if (dto.expectedDuration() == null || dto.expectedDuration() <= 0) errors.add("Thời gian sạc dự kiến phải lớn hơn 0");
        else if (dto.expectedDuration() > MAX_DURATION_MINUTES) errors.add("Thời gian sạc tối đa " + MAX_DURATION_MINUTES + " phút");
        if (dto.personalVehicleId() != null && dto.batteryLevel() == null) errors.add("Nhập mức pin hiện tại của xe");
        if (dto.batteryLevel() != null && (dto.batteryLevel() < 0 || dto.batteryLevel() > 100)) errors.add("Mức pin phải trong khoảng 0-100");
        if (dto.targetBattery() != null && (dto.targetBattery() < 1 || dto.targetBattery() > 100)) errors.add("Mức pin mục tiêu phải trong khoảng 1-100");
        if (dto.batteryLevel() != null && dto.targetBattery() != null && dto.targetBattery() <= dto.batteryLevel()) {
            errors.add("Mức pin mục tiêu phải lớn hơn mức pin hiện tại");
        }
        LocalDateTime now = LocalDateTime.now();
        if (dto.startTime() != null) {
            if (dto.startTime().isBefore(now.minusMinutes(5))) errors.add("Thời gian bắt đầu đã qua");
            if (dto.startTime().isAfter(now.plusHours(24))) errors.add("Chỉ đăng ký trước tối đa 24 giờ");
        }
        return errors;
    }

    /** Free port of the Hub and area for the requested time window; null if none. */
    @Transactional
    public ChargingPort findAvailablePort(Long hubId, String area, LocalDateTime start, int duration) {
        List<ChargingPort> free = ports.findAvailable(hubId, area, start, start.plusMinutes(duration));
        return free.isEmpty() ? null : free.get(0);
    }

    /** Assigns the port to the request (ASSIGNED) and saves it. */
    @Transactional
    public void assignPort(ChargingRequest request, ChargingPort port) {
        scheduler.doAssign(request, port);
    }

    /** WAITING, adds the request to the waiting queue and returns its position. */
    @Transactional
    public int joinWaitingQueue(ChargingRequest request) {
        if (request.getStatus() == RequestStatus.CREATED) request.moveToWaiting();
        WaitingQueue queue = scheduler.queueOf(request.getHubId());
        queue.enqueue(request);
        queue.reorder();
        int pos = queue.getPosition(request);
        requests.save(request);
        notification.notifyUser(request.getUserId(), AlertType.QUEUE_UPDATE, "Đang chờ cổng sạc",
                "Yêu cầu #" + request.getId() + " đang ở vị trí " + pos + " trong hàng chờ (ưu tiên " + request.getPriority() + ").");
        return pos;
    }

    // ---- UC-F06-04 ---------------------------------------------------------------------------

    /** Starts the session when the vehicle is plugged into the assigned port; port and request -&gt; CHARGING. */
    @Transactional
    public ChargingSession startCharging(Long portId, String vehicleRef) {
        ChargingPort port = port(portId);
        ChargingRequest r = requests.findActiveByPort(portId)
                .filter(x -> x.getStatus() == RequestStatus.ASSIGNED)
                .orElseThrow(() -> ApiException.conflict("Cổng " + port.getCode() + " chưa được gán cho yêu cầu nào"));
        if (vehicleRef != null && !vehicleRef.isBlank() && !vehicleRef.equalsIgnoreCase(r.getVehicleRef())) {
            notification.notifyOperators(AlertType.GENERAL, "Cắm sai cổng",
                    "Xe " + vehicleRef + " cắm vào cổng " + port.getCode() + " đang dành cho " + r.getVehicleRef() + ".");
            throw ApiException.conflict("Xe " + vehicleRef + " cắm sai cổng, cổng này dành cho " + r.getVehicleRef());
        }
        LocalDateTime now = LocalDateTime.now();
        r.startCharging();
        port.startCharging();
        r.startSession(now);
        if (r.getVehicle() != null && r.getVehicle().getStatus() == VehicleStatus.AVAILABLE) {
            vehicleState.changeStatus(r.getVehicle().getId(), VehicleStatus.CHARGING);
        }
        requests.save(r);
        ports.save(port);
        notification.notifyUser(r.getUserId(), AlertType.GENERAL, "Bắt đầu sạc",
                "Xe " + r.getVehicleRef() + " bắt đầu sạc tại cổng " + port.getCode() + " với pin " + r.getBatteryLevel() + "%.");
        scheduler.publish(r);
        return r.getSession();
    }

    /** Updates the battery of the running session and completes it when charging is finished. */
    @Transactional
    public void handleBatteryUpdate(Long portId, int level, Double voltage, Double current, Double temperature) {
        ChargingPort port = port(portId);
        port.recordReading(voltage, current, temperature);
        ChargingRequest r = requests.findActiveByPort(portId).filter(x -> x.getStatus() == RequestStatus.CHARGING).orElse(null);
        if (r == null || r.getSession() == null) return;
        boolean wasInterrupted = r.getSession().isDataInterrupted();
        r.getSession().updateBattery(level);
        if (r.getVehicle() != null) vehicleState.updateTelemetry(r.getVehicle().getId(), level, null, null);
        if (r.getPersonalVehicle() != null) r.getPersonalVehicle().setBatteryLevel(level);
        if (wasInterrupted) {
            notification.notifyUser(r.getUserId(), AlertType.SENSOR_LOST, "Đã có lại dữ liệu sạc",
                    "Cổng " + port.getCode() + " gửi dữ liệu trở lại, pin hiện tại " + level + "%.");
        }
        scheduler.publish(r);
        if (temperature != null && temperature >= overheatCelsius) {
            log.warn("Port {} overheat {}°C", port.getCode(), temperature);
            if (!incidentReport.hasOpenIncident(IncidentType.OVERHEAT, null, port.getId())) {
                incidentReport.reportIncident(IncidentType.OVERHEAT, SeverityLevel.CRITICAL, port.getHubId(), null, port.getId(),
                        "Quá nhiệt tại cổng " + port.getCode(), "Nhiệt độ pin " + temperature + "°C vượt ngưỡng " + overheatCelsius + "°C", null);
            }
            events.portStatusChanged(port, PortStatus.FIXING);
            return;
        }
        if (r.getSession().isFinished(LocalDateTime.now())) completeCharging(r);
    }

    /** Current battery and status of the charging session (live tracking). */
    @Transactional(readOnly = true)
    public ChargingRequestView getChargingStatus(Long requestId, Account account) {
        ChargingRequest r = ownOrOperator(requestId, account);
        return view(r);
    }

    /** Stops charging early with the current battery level. */
    @Transactional
    public ChargingRequestView stopCharging(Long requestId, Account account) {
        ChargingRequest r = ownOrOperator(requestId, account);
        if (r.getStatus() != RequestStatus.CHARGING) throw ApiException.conflict("Xe không ở trạng thái đang sạc");
        completeCharging(r);
        return view(r);
    }

    /** Session READY, releases the port, notifies the User and gives the port to the next request. */
    @Transactional
    public void completeCharging(ChargingRequest r) {
        r.getSession().markReady();
        r.complete();
        ChargingPort port = r.getAssignedPort();
        port.release();
        ports.save(port);
        if (r.getVehicle() != null && r.getVehicle().getStatus() == VehicleStatus.CHARGING) {
            vehicleState.changeStatus(r.getVehicle().getId(), VehicleStatus.AVAILABLE);
        }
        requests.save(r);
        Integer battery = r.getSession().getCurrentBattery();
        notification.notifyUser(r.getUserId(), AlertType.CHARGING_COMPLETED, "Sạc xong",
                "Xe " + r.getVehicleRef() + " đã sạc xong tại cổng " + port.getCode() + " (pin " + battery + "%). Vui lòng rút sạc.");
        scheduler.publish(r);
        events.onChargingCompleted(r);
    }

    /** Marks the session data as interrupted and warns the User and the Operators (sent once). */
    @Transactional
    public void handleConnectionLost(Long portId) {
        ChargingPort port = port(portId);
        ChargingRequest r = requests.findActiveByPort(portId).filter(x -> x.getStatus() == RequestStatus.CHARGING).orElse(null);
        if (r == null || r.getSession() == null || r.getSession().isDataInterrupted()) return;
        r.getSession().markDataInterrupted();
        requests.save(r);
        notification.notifyUser(r.getUserId(), AlertType.SENSOR_LOST, "Mất dữ liệu sạc",
                "Cổng " + port.getCode() + " ngừng gửi dữ liệu. Đang hiển thị mức pin gần nhất (" + r.getSession().getCurrentBattery() + "%).");
        notification.notifyOperators(AlertType.SENSOR_LOST, "Mất dữ liệu cảm biến",
                "Cổng " + port.getCode() + " (yêu cầu #" + r.getId() + ") không gửi dữ liệu.");
        scheduler.publish(r);
    }

    // ---- UC-F06-05 ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChargingRequestView> getRequestsByUser(Long userId) {
        return requests.findByUser_IdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    /** Cancels a WAITING or ASSIGNED request of the User and calls SmartScheduler.onRequestReleased(). */
    @Transactional
    public ChargingRequestView cancelRequest(Long requestId, Account account) {
        ChargingRequest r = ownOrOperator(requestId, account);
        r.cancel();
        requests.save(r);
        events.onRequestReleased(r);
        return view(r);
    }

    /** Every minute: ASSIGNED requests not plugged in after 15 minutes become EXPIRED. Returns the count. */
    @Scheduled(fixedDelay = 60000, initialDelay = 45000)
    @Transactional
    public int expireOverdueRequests() {
        return expireOverdueRequests(LocalDateTime.now());
    }

    @Transactional
    public int expireOverdueRequests(LocalDateTime now) {
        int n = 0;
        for (ChargingRequest r : requests.findOverdue(now)) {
            r.expire();
            requests.save(r);
            notification.notifyUser(r.getUserId(), AlertType.CHARGING_CANCELLED, "Yêu cầu sạc hết hạn",
                    "Bạn không cắm sạc trong " + ChargingRequest.ASSIGN_TIMEOUT_MINUTES + " phút nên yêu cầu #" + r.getId() + " đã bị huỷ.");
            events.onRequestReleased(r);
            n++;
        }
        return n;
    }

    /** Every 15 s: a CHARGING session without sensor data for iot.lost-timeout-seconds is marked interrupted. */
    @Scheduled(fixedDelay = 15000, initialDelay = 60000)
    @Transactional
    public void detectLostConnections() {
        LocalDateTime limit = LocalDateTime.now().minusSeconds(lostTimeoutSeconds);
        for (ChargingRequest r : requests.findByStatus(RequestStatus.CHARGING)) {
            ChargingSession s = r.getSession();
            if (s != null && !s.isDataInterrupted() && s.getLastReadingAt() != null && s.getLastReadingAt().isBefore(limit)) {
                handleConnectionLost(r.getAssignedPort().getId());
            }
        }
    }

    // ---- queue (shown to User and Operator) --------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChargingRequestView> getQueue(Long hubId) {
        WaitingQueue q = scheduler.queueOf(hubId);
        List<ChargingRequestView> out = new ArrayList<>();
        for (ChargingRequest r : q.getAll()) out.add(ChargingRequestView.of(r, q.getPosition(r)));
        return out;
    }

    @Transactional(readOnly = true)
    public List<ChargingRequestView> getActiveByHub(Long hubId) {
        return requests.findByHub_IdAndStatusIn(hubId, List.of(RequestStatus.ASSIGNED, RequestStatus.CHARGING))
                .stream().map(this::view).toList();
    }

    // ---- helpers -----------------------------------------------------------------------------

    ChargingRequestView view(ChargingRequest r) {
        Integer pos = r.getStatus() == RequestStatus.WAITING ? scheduler.queueOf(r.getHubId()).getPosition(r) : null;
        return ChargingRequestView.of(r, pos);
    }

    private ChargingRequest ownOrOperator(Long requestId, Account account) {
        ChargingRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("yêu cầu sạc #" + requestId));
        if (!(account instanceof Operator) && !r.getUserId().equals(account.getId())) {
            throw ApiException.forbidden("Không phải yêu cầu sạc của bạn");
        }
        return r;
    }

    private ChargingPort port(Long portId) {
        return ports.findById(portId).orElseThrow(() -> ApiException.notFound("cổng sạc #" + portId));
    }

    private static boolean isReady(ChargingRequest r) {
        return r.getStartTime() == null
                || !r.getStartTime().isAfter(LocalDateTime.now().plusMinutes(SmartScheduler.READY_AHEAD_MINUTES));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim().toUpperCase(); }

    static long minutesBetween(LocalDateTime a, LocalDateTime b) { return Duration.between(a, b).toMinutes(); }
}

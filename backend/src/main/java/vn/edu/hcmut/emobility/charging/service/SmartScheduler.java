package vn.edu.hcmut.emobility.charging.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.charging.dto.ScheduleParams;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.entity.ChargingSchedule;
import vn.edu.hcmut.emobility.charging.entity.ChargingSession;
import vn.edu.hcmut.emobility.charging.entity.PortAssignment;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.ChargingRequestRepository;
import vn.edu.hcmut.emobility.charging.repository.ScheduleRepository;
import vn.edu.hcmut.emobility.charging.repository.WaitingQueueRepository;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.api.IVehicleState;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.PriorityLevel;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.websocket.LiveUpdatePublisher;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.notification.service.NotificationService;

/**
 * «control» SmartScheduler - F-07 Smart charging scheduling.
 * <ul>
 *   <li>Priority: urgent (Priority Override) &gt; near usage time &gt; low battery &gt; high charging demand.</li>
 *   <li>Priority queue: requests are placed in HIGH / MEDIUM / LOW, urgent ones at the head.</li>
 *   <li>Non-preemptive: a vehicle that is CHARGING is never interrupted to give its port to another request.</li>
 *   <li>The schedule contains the ports assigned now (immediate) and the estimated slot of every waiting request.</li>
 * </ul>
 */
@Service
public class SmartScheduler implements IChargingEvents {

    private static final Logger log = LoggerFactory.getLogger(SmartScheduler.class);
    /** A request can receive a port only if its start time is at most this far in the future. */
    public static final int READY_AHEAD_MINUTES = 15;

    private final ChargingRequestRepository requests;
    private final ChargingPortRepository ports;
    private final WaitingQueueRepository queues;
    private final ScheduleRepository schedules;
    private final HubRepository hubs;
    private final NotificationService notificationService;
    private final INotification notification;
    private final IVehicleState vehicleState;
    private final LiveUpdatePublisher live;

    public SmartScheduler(ChargingRequestRepository requests, ChargingPortRepository ports, WaitingQueueRepository queues,
                          ScheduleRepository schedules, HubRepository hubs, NotificationService notificationService,
                          IVehicleState vehicleState, LiveUpdatePublisher live) {
        this.requests = requests;
        this.ports = ports;
        this.queues = queues;
        this.schedules = schedules;
        this.hubs = hubs;
        this.notificationService = notificationService;
        this.notification = notificationService;
        this.vehicleState = vehicleState;
        this.live = live;
    }

    // ---- UC-F07-02: priority ---------------------------------------------------------------

    /** HIGH, MEDIUM or LOW with the default thresholds. */
    public PriorityLevel calculatePriority(ChargingRequest request) {
        return calculatePriority(request, ScheduleParams.defaults(request.getHubId()), LocalDateTime.now());
    }

    /**
     * Criteria in order: near usage time, low battery, high charging demand. An urgent request is always HIGH.
     * <ul>
     *   <li>HIGH: urgent, or next usage within nearUsage minutes, or battery &lt;= lowBattery %.</li>
     *   <li>MEDIUM: next usage within 3 x nearUsage, or battery &lt;= 50 %, or expected duration &gt;= highDemand minutes.</li>
     *   <li>LOW: everything else.</li>
     * </ul>
     */
    public static PriorityLevel calculatePriority(ChargingRequest r, ScheduleParams p, LocalDateTime now) {
        if (r.isUrgent() || r.isResumeFirst()) return PriorityLevel.HIGH;
        Long minutesToUse = r.getNextUsageTime() == null ? null : Duration.between(now, r.getNextUsageTime()).toMinutes();
        if (minutesToUse != null && minutesToUse <= p.nearUsage()) return PriorityLevel.HIGH;
        if (r.getBatteryLevel() <= p.lowBattery()) return PriorityLevel.HIGH;
        if (minutesToUse != null && minutesToUse <= 3L * p.nearUsage()) return PriorityLevel.MEDIUM;
        if (r.getBatteryLevel() <= 50) return PriorityLevel.MEDIUM;
        if (r.getExpectedDuration() >= p.highDemand()) return PriorityLevel.MEDIUM;
        return PriorityLevel.LOW;
    }

    /** Puts the requests into the queue of their Hub sorted HIGH / MEDIUM / LOW (urgent first) and returns it. */
    @Transactional
    public WaitingQueue applyPriorityQueue(Collection<ChargingRequest> list, ScheduleParams params) {
        WaitingQueue queue = null;
        LocalDateTime now = LocalDateTime.now();
        for (ChargingRequest r : list) {
            if (queue == null) queue = queueOf(r.getHubId());
            r.setPriority(calculatePriority(r, params, now));
            if (r.getStatus() == RequestStatus.CREATED) r.moveToWaiting();
            if (r.getStatus() == RequestStatus.WAITING) queue.enqueue(r);
        }
        if (queue != null) queue.reorder();
        return queue;
    }

    // ---- UC-F07-01 / 02: schedule ------------------------------------------------------------

    /**
     * Computes the priority of each request, sorts the priority queue and allocates the ports one by one,
     * then saves the schedule and notifies the Users and Operators.
     */
    @Transactional
    public ChargingSchedule schedule(List<ChargingRequest> list, ScheduleParams params, Long operatorId) {
        Hub hub = hubs.findById(params.hubId()).orElseThrow(() -> ApiException.notFound("trạm"));
        if (!list.isEmpty()) applyPriorityQueue(list, params);
        else queueOf(hub.getId()).reorder();
        return buildSchedule(hub, params, operatorId);
    }

    /**
     * Gives the first free port (of the request's area) to the request. Never interrupts a vehicle that is
     * CHARGING (non-preemptive). Returns null if no port is free; the request then stays WAITING.
     */
    @Transactional
    public PortAssignment assignChargingPort(ChargingRequest request) {
        if (request.getStatus() != RequestStatus.WAITING && request.getStatus() != RequestStatus.CREATED) return null;
        LocalDateTime now = LocalDateTime.now();
        if (request.getStartTime() != null && request.getStartTime().isAfter(now.plusMinutes(READY_AHEAD_MINUTES))) return null;
        List<ChargingPort> free = ports.findAvailable(request.getHubId(), request.getArea(), now,
                now.plusMinutes(request.getExpectedDuration()));
        if (free.isEmpty()) return null;
        ChargingPort port = free.get(0);
        doAssign(request, port);
        return new PortAssignment(request, port, now, now.plusMinutes(request.getExpectedDuration()), true);
    }

    /** Assigns the port to the request (ASSIGNED), removes it from the queue and notifies the User. */
    @Transactional
    public void doAssign(ChargingRequest request, ChargingPort port) {
        port.reserveFor(request.getId());
        request.assignPort(port);
        queueOf(request.getHubId()).remove(request);
        requests.save(request);
        ports.save(port);
        notification.notifyUser(request.getUserId(), AlertType.CHARGING_ASSIGNED, "Đã được gán cổng sạc",
                "Yêu cầu #" + request.getId() + ": mời bạn đến cổng " + port.getCode() + " (khu " + port.getArea()
                        + ") và cắm sạc trong " + ChargingRequest.ASSIGN_TIMEOUT_MINUTES + " phút.");
        publish(request);
    }

    /** Gives every free port to the waiting requests in queue order. */
    @Transactional
    public List<PortAssignment> assignFreePorts(Long hubId) {
        WaitingQueue queue = queueOf(hubId);
        List<PortAssignment> result = new ArrayList<>();
        for (ChargingRequest r : new ArrayList<>(queue.getAll())) {
            PortAssignment a = assignChargingPort(r);
            if (a != null) result.add(a);
        }
        return result;
    }

    /** Marks a WAITING request urgent, moves it to the next position and recalculates the schedule. */
    @Transactional
    public ChargingSchedule markUrgent(Long requestId, String reason, Long operatorId) {
        ChargingRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("yêu cầu sạc #" + requestId));
        if (r.getStatus() != RequestStatus.WAITING) throw ApiException.conflict("Chỉ đánh dấu khẩn cấp yêu cầu đang chờ (WAITING)");
        r.markUrgent(reason);
        queueOf(r.getHubId()).moveToNext(r);
        requests.save(r);
        notification.notifyUser(r.getUserId(), AlertType.QUEUE_UPDATE, "Yêu cầu được ưu tiên",
                "Yêu cầu sạc #" + r.getId() + " được Operator đánh dấu khẩn cấp và chuyển lên đầu hàng chờ.");
        return reschedule(r.getHubId(), operatorId);
    }

    /**
     * UC-F07-03: skips the vehicles that are charging, recalculates the priority of the other requests,
     * reorders the queue, assigns the free ports and sends the new schedule. Warns the Operator when overloaded.
     */
    @Transactional
    public ChargingSchedule reschedule(Long hubId, Long operatorId) {
        Hub hub = hubs.findById(hubId).orElseThrow(() -> ApiException.notFound("trạm"));
        ScheduleParams params = ScheduleParams.defaults(hubId);
        List<ChargingRequest> waiting = requests.findPendingByHub(hubId).stream()
                .filter(r -> r.getStatus() != RequestStatus.ASSIGNED)
                .toList();
        applyPriorityQueue(waiting, params);
        return buildSchedule(hub, params, operatorId);
    }

    // ---- IChargingEvents ---------------------------------------------------------------------

    /** Recalculates the schedule when a new HIGH request arrives; otherwise only gives free ports. */
    @Override
    @Transactional
    public void onNewRequest(ChargingRequest request) {
        if (request.getPriority() == PriorityLevel.HIGH) reschedule(request.getHubId(), null);
        else assignFreePorts(request.getHubId());
    }

    /** Frees the port of a cancelled / expired request so that the next request receives it. */
    @Override
    @Transactional
    public void onRequestReleased(ChargingRequest request) {
        queueOf(request.getHubId()).remove(request);
        ChargingPort port = request.getAssignedPort();
        if (port != null && request.getId().equals(port.getReservedRequestId())) {
            port.release();
            ports.save(port);
        }
        publish(request);
        assignFreePorts(request.getHubId());
    }

    @Override
    @Transactional
    public void onChargingCompleted(ChargingRequest request) {
        assignFreePorts(request.getHubId());
    }

    /**
     * FIXING: ends the session on the port, puts its request back to WAITING (at the head of the queue)
     * and recalculates the schedule. AVAILABLE again: recalculates the schedule.
     */
    @Override
    @Transactional
    public void portStatusChanged(ChargingPort port, PortStatus status) {
        if (status == PortStatus.FIXING) {
            if (port.getStatus() == PortStatus.FIXING) return;
            ChargingRequest affected = requests.findActiveByPort(port.getId()).orElse(null);
            port.markFixing();
            ports.save(port);
            if (affected != null) {
                boolean wasCharging = affected.getStatus() == RequestStatus.CHARGING;
                ChargingSession s = affected.getSession();
                if (wasCharging && s != null) {
                    s.interrupt();
                    if (s.getCurrentBattery() != null) affected.setBatteryLevel(s.getCurrentBattery());
                    if (affected.getVehicle() != null && affected.getVehicle().getStatus() == VehicleStatus.CHARGING) {
                        vehicleState.changeStatus(affected.getVehicle().getId(), VehicleStatus.AVAILABLE);
                    }
                }
                affected.moveToWaiting();
                affected.markResumeFirst();
                queueOf(affected.getHubId()).moveToNext(affected);
                requests.save(affected);
                notification.notifyUser(affected.getUserId(), AlertType.PORT_FAULT, "Cổng sạc gặp sự cố",
                        "Cổng " + port.getCode() + " bị hỏng" + (wasCharging ? " trong lúc sạc" : "")
                                + ". Yêu cầu #" + affected.getId() + " được đưa lên đầu hàng chờ và sẽ được gán cổng khác.");
            }
            log.info("Port {} -> FIXING", port.getCode());
            reschedule(port.getHubId(), null);
        } else if (status == PortStatus.AVAILABLE) {
            if (port.getStatus() != PortStatus.FIXING) return;
            port.markRepaired();
            ports.save(port);
            log.info("Port {} repaired -> AVAILABLE", port.getCode());
            reschedule(port.getHubId(), null);
        }
        live.publish("port", Map.of("portId", port.getId(), "hubId", port.getHubId(), "status", port.getStatus().name()));
    }

    /**
     * Every minute: gives free ports to waiting requests whose start time has come (a request registered for
     * later is not assigned before its start time, so no event would otherwise wake it up).
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 55000)
    @Transactional
    public void dispatchReadyRequests() {
        for (WaitingQueue q : queues.findAll()) {
            if (q.size() > 0) assignFreePorts(q.getHub().getId());
        }
    }

    // ---- internals ---------------------------------------------------------------------------

    /**
     * Builds the schedule: the ports assigned now plus, for each request still waiting, the earliest
     * estimated time a port of its area becomes free (based on the running sessions and earlier plans).
     */
    public ChargingSchedule buildSchedule(Hub hub, ScheduleParams params, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = params.windowEnd() == null ? now.plusHours(12) : params.windowEnd();
        List<PortAssignment> immediate = assignFreePorts(hub.getId());

        ChargingSchedule schedule = new ChargingSchedule(hub, operatorId, params.windowStart() == null ? now : params.windowStart(), windowEnd);
        immediate.forEach(schedule::addAssignment);

        // When does each working port become free?
        Map<ChargingPort, LocalDateTime> freeAt = new HashMap<>();
        for (ChargingPort p : ports.findByHub(hub.getId())) {
            if (p.getStatus() == PortStatus.FIXING) continue;
            LocalDateTime t = now;
            ChargingRequest holder = requests.findActiveByPort(p.getId()).orElse(null);
            if (holder != null) {
                if (holder.getStatus() == RequestStatus.CHARGING && holder.getSession() != null && holder.getSession().getExpectedEnd() != null) {
                    t = holder.getSession().getExpectedEnd().isAfter(now) ? holder.getSession().getExpectedEnd() : now.plusMinutes(5);
                } else {
                    t = now.plusMinutes(holder.getExpectedDuration());
                }
            }
            freeAt.put(p, t);
        }

        WaitingQueue queue = queueOf(hub.getId());
        for (ChargingRequest r : queue.getAll()) {
            ChargingPort best = null;
            for (Map.Entry<ChargingPort, LocalDateTime> e : freeAt.entrySet()) {
                ChargingPort p = e.getKey();
                if (r.getArea() != null && !r.getArea().isBlank() && !r.getArea().equalsIgnoreCase(p.getArea())) continue;
                if (best == null || e.getValue().isBefore(freeAt.get(best))
                        || (e.getValue().isEqual(freeAt.get(best)) && p.getCode().compareTo(best.getCode()) < 0)) {
                    best = p;
                }
            }
            if (best == null) continue;
            LocalDateTime start = freeAt.get(best);
            if (r.getStartTime() != null && r.getStartTime().isAfter(start)) start = r.getStartTime();
            if (!start.isBefore(windowEnd)) continue;
            LocalDateTime end = start.plusMinutes(r.getExpectedDuration());
            schedule.addAssignment(new PortAssignment(r, best, start, end, false));
            freeAt.put(best, end);
        }

        schedule.setWaitingCount(queue.size());
        schedule.setOverloaded(hub.isOverloaded(queue.size()));
        schedule.setNote(immediate.size() + " cổng gán ngay, " + queue.size() + " yêu cầu chờ");
        schedules.save(schedule);
        notificationService.sendScheduleUpdate(schedule, operatorId != null);
        if (schedule.isOverloaded()) {
            notification.notifyOperators(AlertType.HUB_OVERLOAD, "Trạm sạc quá tải",
                    "Trạm " + hub.getName() + " có " + queue.size() + " yêu cầu chờ (ngưỡng " + hub.getOverloadThreshold()
                            + "). Cân nhắc điều phối xe sang trạm khác hoặc mở thêm cổng.");
        }
        live.publish("schedule", Map.of("hubId", hub.getId(), "scheduleId", schedule.getId(), "waiting", queue.size()));
        return schedule;
    }

    public WaitingQueue queueOf(Long hubId) {
        return queues.findByHub_Id(hubId).orElseGet(() -> {
            Hub hub = hubs.findById(hubId).orElseThrow(() -> ApiException.notFound("trạm"));
            return queues.save(new WaitingQueue(hub));
        });
    }

    public void publish(ChargingRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("requestId", r.getId());
        m.put("userId", r.getUserId());
        m.put("hubId", r.getHubId());
        m.put("status", r.getStatus().name());
        m.put("portCode", r.getAssignedPort() == null ? null : r.getAssignedPort().getCode());
        m.put("battery", r.getSession() == null ? r.getBatteryLevel() : r.getSession().getCurrentBattery());
        live.publish("charging", m);
    }
}

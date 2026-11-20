package vn.edu.hcmut.emobility.charging.dto;

import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.entity.ChargingSession;
import vn.edu.hcmut.emobility.common.enums.PriorityLevel;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;

/** What the client sees for a charging request: status, port, queue position and live session data. */
public record ChargingRequestView(Long id, Long userId, Long hubId, String hubName, String area, String vehicleRef,
                                  int batteryLevel, int targetBattery, RequestStatus status, PriorityLevel priority,
                                  boolean urgent, String urgentReason, Long portId, String portCode,
                                  LocalDateTime assignedAt, LocalDateTime plugInDeadline, LocalDateTime startTime,
                                  int expectedDuration, LocalDateTime nextUsageTime, Integer queuePosition,
                                  LocalDateTime createdAt, ChargingSession session) {

    public static ChargingRequestView of(ChargingRequest r, Integer queuePosition) {
        LocalDateTime deadline = null;
        if (r.getAssignedAt() != null) {
            LocalDateTime from = r.getStartTime() != null && r.getStartTime().isAfter(r.getAssignedAt()) ? r.getStartTime() : r.getAssignedAt();
            deadline = from.plusMinutes(ChargingRequest.ASSIGN_TIMEOUT_MINUTES);
        }
        return new ChargingRequestView(r.getId(), r.getUserId(), r.getHubId(), r.getHub().getName(), r.getArea(), r.getVehicleRef(),
                r.getBatteryLevel(), r.getTargetBattery(), r.getStatus(), r.getPriority(), r.isUrgent(), r.getUrgentReason(),
                r.getAssignedPort() == null ? null : r.getAssignedPort().getId(),
                r.getAssignedPort() == null ? null : r.getAssignedPort().getCode(),
                r.getAssignedAt(), deadline, r.getStartTime(), r.getExpectedDuration(), r.getNextUsageTime(),
                queuePosition != null && queuePosition > 0 ? queuePosition : null, r.getCreatedAt(), r.getSession());
    }
}

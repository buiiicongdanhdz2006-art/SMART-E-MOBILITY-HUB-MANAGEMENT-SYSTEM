package vn.edu.hcmut.emobility.charging.dto;

import java.time.LocalDateTime;

/** «DTO» settings of a charging schedule (UC-F07-01). Null thresholds use the defaults of SmartScheduler. */
public record ScheduleParams(Long hubId, LocalDateTime windowStart, LocalDateTime windowEnd,
                             Integer lowBatteryThreshold, Integer nearUsageMinutes, Integer highDemandMinutes) {

    public static ScheduleParams defaults(Long hubId) {
        LocalDateTime now = LocalDateTime.now();
        return new ScheduleParams(hubId, now, now.plusHours(12), null, null, null);
    }

    public int lowBattery() { return lowBatteryThreshold == null ? 20 : lowBatteryThreshold; }
    public int nearUsage() { return nearUsageMinutes == null ? 60 : nearUsageMinutes; }
    public int highDemand() { return highDemandMinutes == null ? 90 : highDemandMinutes; }
}

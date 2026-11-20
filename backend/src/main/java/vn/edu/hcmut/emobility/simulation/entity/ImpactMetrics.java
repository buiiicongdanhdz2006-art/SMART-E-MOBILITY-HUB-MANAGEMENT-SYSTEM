package vn.edu.hcmut.emobility.simulation.entity;

import vn.edu.hcmut.emobility.common.enums.Severity;

/** Comparison between the current state and the simulated state. */
public class ImpactMetrics {

    public double utilizationBefore;
    public double utilizationAfter;
    public double portLoadBefore;
    public double portLoadAfter;
    public double avgWaitMinutesBefore;
    public double avgWaitMinutesAfter;
    public double unmetChargingBefore;
    public double unmetChargingAfter;
    public double vehicleShortageBefore;
    public double vehicleShortageAfter;

    /** Maps the deviation metrics to an overall severity level (LOW to CRITICAL). */
    public Severity getOverallSeverity() {
        if (avgWaitMinutesAfter >= 60 || portLoadAfter >= 1.0 || vehicleShortageAfter >= 5) return Severity.CRITICAL;
        if (avgWaitMinutesAfter >= 30 || portLoadAfter >= 0.85 || vehicleShortageAfter >= 3) return Severity.HIGH;
        if (avgWaitMinutesAfter >= 15 || portLoadAfter >= 0.7 || vehicleShortageAfter >= 1) return Severity.MEDIUM;
        return Severity.LOW;
    }

    /** Summary string of the impact metrics for display. */
    public String toSummary() {
        return String.format("Tải cổng sạc %.0f%% -> %.0f%%, chờ TB %.0f -> %.0f phút, thiếu xe %.1f -> %.1f xe/giờ, "
                        + "nhu cầu sạc không đáp ứng %.1f -> %.1f lượt/ngày. Mức độ: %s",
                portLoadBefore * 100, portLoadAfter * 100, avgWaitMinutesBefore, avgWaitMinutesAfter,
                vehicleShortageBefore, vehicleShortageAfter, unmetChargingBefore, unmetChargingAfter, getOverallSeverity());
    }
}

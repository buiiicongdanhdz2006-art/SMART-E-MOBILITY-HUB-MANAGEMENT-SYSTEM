package vn.edu.hcmut.emobility.charging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.common.enums.ChargingState;

/** Charging session of a request (composition: ChargingRequest contains ChargingSession). */
@Embeddable
public class ChargingSession {

    @Column(name = "session_initial_battery")
    private Integer initialBattery;

    @Column(name = "session_current_battery")
    private Integer currentBattery;

    @Column(name = "session_target_battery")
    private Integer targetBattery;

    @Column(name = "session_started_at")
    private LocalDateTime startedAt;

    @Column(name = "session_expected_end")
    private LocalDateTime expectedEnd;

    @Column(name = "session_ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_state")
    private ChargingState state;

    @Column(name = "session_data_interrupted")
    private Boolean dataInterrupted;

    @Column(name = "session_last_reading_at")
    private LocalDateTime lastReadingAt;

    public ChargingSession() {}

    /** Records the initial battery, the start time and sets state START. */
    public void start(int battery, int target, int expectedMinutes, LocalDateTime now) {
        this.initialBattery = battery;
        this.currentBattery = battery;
        this.targetBattery = target;
        this.startedAt = now;
        this.expectedEnd = now.plusMinutes(expectedMinutes);
        this.lastReadingAt = now;
        this.dataInterrupted = false;
        this.state = ChargingState.START;
    }

    /** Updates "Battery now" and moves to CHARGING. */
    public void updateBattery(int level) {
        this.currentBattery = Math.max(0, Math.min(100, level));
        this.lastReadingAt = LocalDateTime.now();
        this.dataInterrupted = false;
        if (state == ChargingState.START || state == ChargingState.CHARGING) this.state = ChargingState.CHARGING;
    }

    /** True if the battery reached the target or the expected charging time is over. */
    public boolean isFinished(LocalDateTime now) {
        if (currentBattery != null && targetBattery != null && currentBattery >= targetBattery) return true;
        return expectedEnd != null && !now.isBefore(expectedEnd);
    }

    /** Ready: battery full, time over or stopped by the User. */
    public void markReady() {
        this.state = ChargingState.READY;
        this.endedAt = LocalDateTime.now();
    }

    /** Ends the session because the port broke while charging. */
    public void interrupt() {
        this.state = ChargingState.INTERRUPTED;
        this.endedAt = LocalDateTime.now();
    }

    /** Sensor data lost: the last data is shown with a warning. */
    public void markDataInterrupted() { this.dataInterrupted = true; }

    public boolean isRunning() { return state == ChargingState.START || state == ChargingState.CHARGING; }

    public Integer getInitialBattery() { return initialBattery; }
    public Integer getCurrentBattery() { return currentBattery; }
    public Integer getTargetBattery() { return targetBattery; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getExpectedEnd() { return expectedEnd; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public ChargingState getState() { return state; }
    public boolean isDataInterrupted() { return Boolean.TRUE.equals(dataInterrupted); }
    public LocalDateTime getLastReadingAt() { return lastReadingAt; }
}

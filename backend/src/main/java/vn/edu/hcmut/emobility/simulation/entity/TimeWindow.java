package vn.edu.hcmut.emobility.simulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.LocalDateTime;

/** Time window of a scenario (composition of Scenario). */
@Embeddable
public class TimeWindow {

    @Column(name = "window_start")
    private LocalDateTime start;

    @Column(name = "window_end")
    private LocalDateTime end;

    protected TimeWindow() {}

    public TimeWindow(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    /** True if the start time is before the end time. */
    public boolean isValid() { return start != null && end != null && start.isBefore(end); }

    public Duration getDuration() { return isValid() ? Duration.between(start, end) : Duration.ZERO; }

    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
}

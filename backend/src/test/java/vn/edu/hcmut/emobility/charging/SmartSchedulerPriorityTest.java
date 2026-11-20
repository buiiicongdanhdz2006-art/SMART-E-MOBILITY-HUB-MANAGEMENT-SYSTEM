package vn.edu.hcmut.emobility.charging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmut.emobility.charging.dto.ScheduleParams;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.service.SmartScheduler;
import vn.edu.hcmut.emobility.common.enums.PriorityLevel;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** Priority rules and priority queue of F-07 (UC-F07-02). */
class SmartSchedulerPriorityTest {

    private final Hub hub = new Hub("H01", "Hub 1", "addr", 10.87, 106.80, 5);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 20, 9, 0);
    private final ScheduleParams params = new ScheduleParams(1L, now, now.plusHours(8), null, null, null);
    private long ids = 1;

    private ChargingRequest req(int battery, Integer minutesToUse, int duration, int createdOffset) {
        ChargingRequest r = new ChargingRequest(null, hub, null, null, null, battery, 100,
                minutesToUse == null ? null : now.plusMinutes(minutesToUse), now, duration);
        r.setIdForTest(ids++);
        r.setCreatedAtForTest(now.plusMinutes(createdOffset));
        r.setPriority(SmartScheduler.calculatePriority(r, params, now));
        return r;
    }

    @Test
    void nearUsageTimeIsHigh() {
        assertEquals(PriorityLevel.HIGH, req(80, 45, 30, 0).getPriority());
    }

    @Test
    void lowBatteryIsHigh() {
        assertEquals(PriorityLevel.HIGH, req(15, null, 30, 0).getPriority());
    }

    @Test
    void mediumRules() {
        assertEquals(PriorityLevel.MEDIUM, req(80, 150, 30, 0).getPriority()); // usage within 3h
        assertEquals(PriorityLevel.MEDIUM, req(45, null, 30, 0).getPriority()); // battery <= 50
        assertEquals(PriorityLevel.MEDIUM, req(80, null, 120, 0).getPriority()); // high demand
    }

    @Test
    void otherwiseLow() {
        assertEquals(PriorityLevel.LOW, req(80, null, 30, 0).getPriority());
    }

    @Test
    void customThresholds() {
        ScheduleParams strict = new ScheduleParams(1L, now, now.plusHours(8), 40, 30, 60);
        ChargingRequest r = new ChargingRequest(null, hub, null, null, null, 35, 100, now.plusMinutes(45), now, 30);
        assertEquals(PriorityLevel.HIGH, SmartScheduler.calculatePriority(r, strict, now)); // battery 35 <= 40
    }

    @Test
    void queueOrderUrgentThenPriorityThenUsageThenBatteryThenFifo() {
        WaitingQueue q = new WaitingQueue(hub);
        ChargingRequest low = req(80, null, 30, 0);
        ChargingRequest medium = req(45, null, 30, 1);
        ChargingRequest highBattery = req(10, null, 30, 2);
        ChargingRequest highUsage = req(70, 20, 30, 3);
        ChargingRequest urgent = req(90, null, 30, 4);
        urgent.markUrgent("Ưu tiên");
        for (ChargingRequest r : List.of(low, medium, highBattery, highUsage, urgent)) q.enqueue(r);
        q.reorder();
        assertEquals(List.of(urgent, highUsage, highBattery, medium, low), q.getAll());
        assertEquals(1, q.getPosition(urgent));
        assertEquals(5, q.getPosition(low));
    }

    @Test
    void requestInterruptedByBrokenPortIsServedRightAfterUrgentOnes() {
        WaitingQueue q = new WaitingQueue(hub);
        ChargingRequest high = req(10, 20, 30, 0);       // HIGH by battery and usage time
        ChargingRequest resumed = req(80, null, 30, 1);  // would be LOW
        resumed.markResumeFirst();
        resumed.setPriority(SmartScheduler.calculatePriority(resumed, params, now));
        ChargingRequest urgent = req(90, null, 30, 2);
        urgent.markUrgent("Ưu tiên");
        for (ChargingRequest r : List.of(high, resumed, urgent)) q.enqueue(r);
        q.reorder();
        assertEquals(PriorityLevel.HIGH, resumed.getPriority());
        assertEquals(List.of(urgent, resumed, high), q.getAll());
    }

    @Test
    void moveToNextAndRemove() {
        WaitingQueue q = new WaitingQueue(hub);
        ChargingRequest a = req(80, null, 30, 0);
        ChargingRequest b = req(80, null, 30, 1);
        ChargingRequest c = req(80, null, 30, 2);
        q.enqueue(a);
        q.enqueue(b);
        assertEquals(3, q.enqueue(c));
        assertEquals(3, q.enqueue(c)); // no duplicates
        q.moveToNext(c);
        assertEquals(1, q.getPosition(c));
        q.remove(a);
        assertEquals(-1, q.getPosition(a));
        assertEquals(2, q.size());
    }
}

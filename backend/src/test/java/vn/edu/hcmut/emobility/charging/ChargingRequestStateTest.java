package vn.edu.hcmut.emobility.charging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.common.enums.ChargingState;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.PriorityLevel;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** State charts of ChargingRequest, ChargingSession and ChargingPort (F-06). */
class ChargingRequestStateTest {

    private Hub hub;
    private ChargingPort port;
    private ChargingRequest request;

    @BeforeEach
    void setUp() {
        hub = new Hub("H01", "Hub 1", "addr", 10.87, 106.80, 5);
        port = new ChargingPort("H01-P01", hub, "A", 7.0);
        request = new ChargingRequest(null, hub, "A", null, null, 30, 100, null, LocalDateTime.now(), 60);
        request.setIdForTest(1L);
    }

    @Test
    void fullLifecycle_assignChargeComplete() {
        port.reserveFor(1L);
        request.assignPort(port);
        assertEquals(RequestStatus.ASSIGNED, request.getStatus());
        assertFalse(port.isFree());

        request.startCharging();
        port.startCharging();
        request.startSession(LocalDateTime.now());
        assertEquals(RequestStatus.CHARGING, request.getStatus());
        assertEquals(PortStatus.CHARGING, port.getStatus());
        assertEquals(ChargingState.START, request.getSession().getState());

        request.getSession().updateBattery(70);
        assertEquals(ChargingState.CHARGING, request.getSession().getState());
        assertFalse(request.getSession().isFinished(LocalDateTime.now()));
        request.getSession().updateBattery(100);
        assertTrue(request.getSession().isFinished(LocalDateTime.now()));

        request.getSession().markReady();
        request.complete();
        port.release();
        assertEquals(RequestStatus.COMPLETED, request.getStatus());
        assertEquals(ChargingState.READY, request.getSession().getState());
        assertTrue(port.isFree());
    }

    @Test
    void sessionFinishesWhenExpectedTimeIsOver() {
        request.assignPort(port);
        request.startCharging();
        LocalDateTime start = LocalDateTime.now();
        request.startSession(start);
        assertFalse(request.getSession().isFinished(start.plusMinutes(59)));
        assertTrue(request.getSession().isFinished(start.plusMinutes(60)));
    }

    @Test
    void cannotCancelWhileCharging() {
        request.assignPort(port);
        request.startCharging();
        assertThrows(IllegalStateException.class, () -> request.cancel());
    }

    @Test
    void cancelWaitingOrAssigned() {
        request.moveToWaiting();
        request.cancel();
        assertEquals(RequestStatus.CANCELLED, request.getStatus());
        assertThrows(IllegalStateException.class, () -> request.assignPort(port));
    }

    @Test
    void overdueAfterFifteenMinutesWithoutPlugIn() {
        request.assignPort(port);
        LocalDateTime assigned = request.getAssignedAt();
        assertFalse(request.isOverdue(assigned.plusMinutes(14)));
        assertTrue(request.isOverdue(assigned.plusMinutes(16)));
        request.expire();
        assertEquals(RequestStatus.EXPIRED, request.getStatus());
    }

    @Test
    void portBrokenWhileChargingPutsRequestBackToWaiting() {
        request.assignPort(port);
        request.startCharging();
        port.startCharging();
        request.startSession(LocalDateTime.now());
        port.markFixing();
        request.getSession().interrupt();
        request.moveToWaiting();
        assertEquals(RequestStatus.WAITING, request.getStatus());
        assertNull(request.getAssignedPort());
        assertEquals(ChargingState.INTERRUPTED, request.getSession().getState());
        assertEquals(PortStatus.FIXING, port.getStatus());
        port.markRepaired();
        assertTrue(port.isFree());
    }

    @Test
    void urgentRequiresReasonAndIsAlwaysHigh() {
        assertThrows(IllegalArgumentException.class, () -> request.markUrgent(" "));
        request.markUrgent("Xe cứu thương nội bộ");
        assertTrue(request.isUrgent());
        request.setPriority(PriorityLevel.LOW);
        assertEquals(PriorityLevel.HIGH, request.getPriority());
    }

    @Test
    void portCannotBeReservedTwice() {
        port.reserveFor(1L);
        assertThrows(IllegalStateException.class, () -> port.reserveFor(2L));
    }
}

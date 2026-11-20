package vn.edu.hcmut.emobility.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import vn.edu.hcmut.emobility.common.enums.ScenarioType;
import vn.edu.hcmut.emobility.common.enums.Severity;
import vn.edu.hcmut.emobility.simulation.entity.HubState;
import vn.edu.hcmut.emobility.simulation.entity.Scenario;
import vn.edu.hcmut.emobility.simulation.entity.SimulationResult;
import vn.edu.hcmut.emobility.simulation.entity.TimeWindow;
import vn.edu.hcmut.emobility.simulation.service.SimulationEngine;

/** What-if simulation engine of F-10. */
class SimulationEngineTest {

    private final SimulationEngine engine = new SimulationEngine();

    private HubState state() {
        HubState s = new HubState();
        s.hubId = 1L;
        s.hubName = "Metro";
        s.totalVehicles = 10;
        s.availableVehicles = 6;
        s.totalPorts = 6;
        s.workingPorts = 6;
        s.portThroughputPerHour = 1.0;
        s.chargingDemandPerHour = 3.0;
        s.vehicleDemandPerHour = 5;
        s.openHour = 5;
        s.closeHour = 23;
        return s;
    }

    private Scenario scenario(ScenarioType type) {
        LocalDateTime t = LocalDateTime.of(2026, 11, 20, 7, 0);
        return new Scenario(type, 1L, new TimeWindow(t, t.plusHours(2)), 1L);
    }

    @Test
    void hardwareFailureIncreasesWaitAndLoad() {
        Scenario s = scenario(ScenarioType.HARDWARE_FAILURE);
        s.setParameter("failedPorts", 3);
        SimulationResult r = engine.runSimulation(s, state());
        assertTrue(r.isSuccessful());
        assertEquals(0.5, r.getMetrics().portLoadBefore);
        assertEquals(1.0, r.getMetrics().portLoadAfter);
        assertEquals(SimulationEngine.MAX_WAIT_MINUTES, r.getMetrics().avgWaitMinutesAfter);
        assertEquals(Severity.CRITICAL, r.getMetrics().getOverallSeverity());
        assertEquals(6, r.getBefore().workingPorts); // real state untouched
    }

    @Test
    void congestionMultipliesDemand() {
        Scenario s = scenario(ScenarioType.CONGESTION_SETTING);
        s.setParameter("demandMultiplier", 2);
        SimulationResult r = engine.runSimulation(s, state());
        assertEquals(6.0, r.getAfter().chargingDemandPerHour);
        assertEquals(4.0, r.getMetrics().vehicleShortageAfter);
    }

    @Test
    void shorterHoursSqueezeDemand() {
        Scenario s = scenario(ScenarioType.HOURS_SETTING);
        s.setParameter("openHour", 6);
        s.setParameter("closeHour", 15); // 18h -> 9h, demand x2
        SimulationResult r = engine.runSimulation(s, state());
        assertEquals(6.0, r.getAfter().chargingDemandPerHour);
    }

    @Test
    void capacityLimitRemovesVehicles() {
        Scenario s = scenario(ScenarioType.CAPACITY_LIMIT);
        s.setParameter("maxVehicles", 7);
        SimulationResult r = engine.runSimulation(s, state());
        assertEquals(7, r.getAfter().totalVehicles);
        assertEquals(3, r.getAfter().availableVehicles);
    }

    @Test
    void impossibleScenarioFails() {
        Scenario s = scenario(ScenarioType.HARDWARE_FAILURE);
        s.setParameter("failedPorts", 10);
        assertThrows(IllegalArgumentException.class, () -> engine.runSimulation(s, state()));
        assertTrue(engine.isAvailable()); // engine released after failure
    }

    @Test
    void rerunCopyDoesNotChangeOriginal() {
        Scenario s = scenario(ScenarioType.CONGESTION_SETTING);
        s.setParameter("demandMultiplier", 1.5);
        Scenario copy = s.copy();
        copy.setParameter("demandMultiplier", 3);
        assertEquals("1.5", s.getParameter("demandMultiplier"));
    }
}

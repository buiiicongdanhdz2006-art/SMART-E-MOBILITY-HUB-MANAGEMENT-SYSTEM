package vn.edu.hcmut.emobility.simulation.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.emobility.simulation.entity.HubState;
import vn.edu.hcmut.emobility.simulation.entity.ImpactMetrics;
import vn.edu.hcmut.emobility.simulation.entity.Scenario;
import vn.edu.hcmut.emobility.simulation.entity.SimulationResult;

/**
 * «control» SimulationEngine (F-10). Deterministic peak-hour model:
 * <ul>
 *   <li>Charging is a queue with c = working ports, service rate mu = port throughput per hour and arrival
 *       rate lambda = charging demand per hour. Port load rho = lambda / (c * mu); average wait
 *       = rho / (1 - rho) * (60 / mu) / c minutes (capped at 120 when rho &gt;= 1).</li>
 *   <li>Vehicle shortage = max(0, vehicle demand per hour - available vehicles).</li>
 * </ul>
 */
@Component
public class SimulationEngine {

    public static final double MAX_WAIT_MINUTES = 120;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /** True if the engine is ready to accept a simulation request (F10-PRE-04). */
    public boolean isAvailable() { return !busy.get(); }

    /** Runs the scenario against the current state; throws if the engine fails (F10-AF-02). */
    public SimulationResult runSimulation(Scenario scenario, HubState state) {
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("Engine đang chạy một mô phỏng khác");
        try {
            HubState before = state.copy();
            HubState after = applyScenario(scenario, state.copy());
            return SimulationResult.success(scenario, before, after, computeMetrics(before, after));
        } finally {
            busy.set(false);
        }
    }

    /** Applies the scenario changes to a copy of the current state. */
    public HubState applyScenario(Scenario s, HubState st) {
        switch (s.getType()) {
            case HOURS_SETTING -> {
                int oldHours = Math.max(1, st.getOpenHours());
                Double open = s.getNumber("openHour");
                Double close = s.getNumber("closeHour");
                if (open != null) st.openHour = open.intValue();
                if (close != null) st.closeHour = close.intValue();
                int newHours = st.getOpenHours();
                if (newHours <= 0) throw new IllegalArgumentException("Giờ mở cửa phải trước giờ đóng cửa");
                double factor = oldHours / (double) newHours; // same daily demand squeezed into fewer hours
                st.vehicleDemandPerHour *= factor;
                st.chargingDemandPerHour *= factor;
            }
            case CAPACITY_LIMIT -> {
                Double maxVehicles = s.getNumber("maxVehicles");
                Double maxPorts = s.getNumber("maxPorts");
                Double maxSlots = s.getNumber("maxSlots");
                if (maxVehicles != null && maxVehicles < st.totalVehicles) {
                    int removed = st.totalVehicles - maxVehicles.intValue();
                    st.totalVehicles = maxVehicles.intValue();
                    st.availableVehicles = Math.max(0, st.availableVehicles - removed);
                }
                if (maxPorts != null) {
                    st.totalPorts = maxPorts.intValue();
                    st.workingPorts = Math.min(st.workingPorts, st.totalPorts);
                }
                if (maxSlots != null && maxSlots < st.totalSlots) {
                    int removed = st.totalSlots - maxSlots.intValue();
                    st.totalSlots = maxSlots.intValue();
                    st.freeSlots = Math.max(0, st.freeSlots - removed);
                }
            }
            case HARDWARE_FAILURE -> {
                Double failedPorts = s.getNumber("failedPorts");
                Double failedVehicles = s.getNumber("failedVehicles");
                if (failedPorts != null) {
                    if (failedPorts > st.workingPorts) throw new IllegalArgumentException("Số cổng hỏng (" + failedPorts.intValue()
                            + ") lớn hơn số cổng đang hoạt động (" + st.workingPorts + ")");
                    st.workingPorts -= failedPorts.intValue();
                }
                if (failedVehicles != null) st.availableVehicles = Math.max(0, st.availableVehicles - failedVehicles.intValue());
            }
            case CONGESTION_SETTING -> {
                Double m = s.getNumber("demandMultiplier");
                double mult = m == null ? 1.5 : m;
                st.vehicleDemandPerHour *= mult;
                st.chargingDemandPerHour *= mult;
                st.waitingRequests = (int) Math.round(st.waitingRequests * mult);
            }
        }
        return st;
    }

    /** Compares the current state with the simulated state. */
    public ImpactMetrics computeMetrics(HubState before, HubState after) {
        ImpactMetrics m = new ImpactMetrics();
        m.utilizationBefore = round(before.getUtilizationRate());
        m.utilizationAfter = round(after.getUtilizationRate());
        m.portLoadBefore = round(portLoad(before));
        m.portLoadAfter = round(portLoad(after));
        m.avgWaitMinutesBefore = round(avgWait(before));
        m.avgWaitMinutesAfter = round(avgWait(after));
        m.unmetChargingBefore = round(unmetCharging(before));
        m.unmetChargingAfter = round(unmetCharging(after));
        m.vehicleShortageBefore = round(Math.max(0, before.vehicleDemandPerHour - before.availableVehicles));
        m.vehicleShortageAfter = round(Math.max(0, after.vehicleDemandPerHour - after.availableVehicles));
        return m;
    }

    static double portLoad(HubState s) {
        double capacity = s.workingPorts * s.portThroughputPerHour;
        return capacity <= 0 ? (s.chargingDemandPerHour > 0 ? 9.99 : 0) : s.chargingDemandPerHour / capacity;
    }

    static double avgWait(HubState s) {
        double rho = portLoad(s);
        if (s.workingPorts == 0 || rho >= 0.98) return MAX_WAIT_MINUTES;
        double w = rho / (1 - rho) * (60.0 / s.portThroughputPerHour) / s.workingPorts;
        return Math.min(MAX_WAIT_MINUTES, w);
    }

    /** Charging sessions per day that cannot be served. */
    static double unmetCharging(HubState s) {
        double capacity = s.workingPorts * s.portThroughputPerHour;
        return Math.max(0, s.chargingDemandPerHour - capacity) * s.getOpenHours();
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}

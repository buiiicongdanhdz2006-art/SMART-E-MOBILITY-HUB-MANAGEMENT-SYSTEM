package vn.edu.hcmut.emobility.fleet.dto;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** «DTO» numbers of the fleet status dashboard. */
public class FleetStatistics {

    private int total;
    private final Map<VehicleStatus, Integer> byStatus = new LinkedHashMap<>();
    private double averageBattery;
    private int lowBattery;

    /** Counts the vehicles by status and returns the dashboard numbers. */
    public static FleetStatistics calculate(Collection<Vehicle> vehicles) {
        FleetStatistics s = new FleetStatistics();
        for (VehicleStatus st : VehicleStatus.values()) s.byStatus.put(st, 0);
        int sum = 0;
        for (Vehicle v : vehicles) {
            s.total++;
            s.byStatus.merge(v.getStatus(), 1, Integer::sum);
            sum += v.getBatteryLevel();
            if (v.getBatteryLevel() < 20) s.lowBattery++;
        }
        s.averageBattery = s.total == 0 ? 0 : Math.round(sum * 10.0 / s.total) / 10.0;
        return s;
    }

    /** Number of vehicles in the given status. */
    public int getCount(VehicleStatus status) { return byStatus.getOrDefault(status, 0); }

    public int getTotal() { return total; }
    public Map<VehicleStatus, Integer> getByStatus() { return byStatus; }
    public double getAverageBattery() { return averageBattery; }
    public int getLowBattery() { return lowBattery; }
}

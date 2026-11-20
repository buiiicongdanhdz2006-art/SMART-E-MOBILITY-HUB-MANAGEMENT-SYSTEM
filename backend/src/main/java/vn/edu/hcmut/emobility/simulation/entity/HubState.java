package vn.edu.hcmut.emobility.simulation.entity;

/** Operational state of a Hub used by the simulation and the monitoring dashboard (shared by F-10, F-11). */
public class HubState {

    public Long hubId;
    public String hubName;
    public int totalVehicles;
    public int availableVehicles;
    public int totalSlots;
    public int freeSlots;
    public int totalPorts;
    public int workingPorts;
    public int chargingNow;
    public int waitingRequests;
    public int activeBookings;
    public double vehicleDemandPerHour;
    public double chargingDemandPerHour;
    /** Vehicles one port can charge per hour (60 / average charging minutes). */
    public double portThroughputPerHour = 1.0;
    public int openHour = 5;
    public int closeHour = 23;

    /** (totalVehicles - availableVehicles) / totalVehicles. */
    public float getUtilizationRate() {
        return totalVehicles == 0 ? 0f : (totalVehicles - availableVehicles) / (float) totalVehicles;
    }

    public int getOpenHours() { return Math.max(0, closeHour - openHour); }

    /** Copy so that the simulation never modifies the real data. */
    public HubState copy() {
        HubState s = new HubState();
        s.hubId = hubId;
        s.hubName = hubName;
        s.totalVehicles = totalVehicles;
        s.availableVehicles = availableVehicles;
        s.totalSlots = totalSlots;
        s.freeSlots = freeSlots;
        s.totalPorts = totalPorts;
        s.workingPorts = workingPorts;
        s.chargingNow = chargingNow;
        s.waitingRequests = waitingRequests;
        s.activeBookings = activeBookings;
        s.vehicleDemandPerHour = vehicleDemandPerHour;
        s.chargingDemandPerHour = chargingDemandPerHour;
        s.portThroughputPerHour = portThroughputPerHour;
        s.openHour = openHour;
        s.closeHour = closeHour;
        return s;
    }
}

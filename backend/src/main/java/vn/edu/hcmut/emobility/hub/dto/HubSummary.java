package vn.edu.hcmut.emobility.hub.dto;

import vn.edu.hcmut.emobility.common.enums.HubState;
import vn.edu.hcmut.emobility.common.enums.LoadLevel;

/** Summary of a Hub shown in the Hub list (UC-F02-01). distanceKm is set by findNearestHubs. */
public record HubSummary(Long id, String code, String name, String address, double latitude, double longitude,
                         HubState state, int freeSlots, int freePorts, int availableVehicles, LoadLevel loadLevel,
                         Double distanceKm) {

    public HubSummary withDistance(double km) {
        return new HubSummary(id, code, name, address, latitude, longitude, state, freeSlots, freePorts,
                availableVehicles, loadLevel, Math.round(km * 100) / 100.0);
    }
}

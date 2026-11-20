package vn.edu.hcmut.emobility.hub.dto;

import java.time.LocalDateTime;
import java.util.Map;
import vn.edu.hcmut.emobility.common.enums.HubState;
import vn.edu.hcmut.emobility.common.enums.LoadLevel;

/** Detailed status of a Hub (UC-F02-02). */
public record HubStatus(Long hubId, String name, HubState state,
                        int totalSlots, int freeSlots, int reservedSlots, int occupiedSlots,
                        int totalPorts, int freePorts, int chargingPorts, int fixingPorts, int waitingRequests,
                        int totalVehicles, Map<String, Integer> vehiclesByStatus, int expectedDemand,
                        double utilisation, LoadLevel loadLevel, LocalDateTime lastUpdated) {}

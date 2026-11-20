package vn.edu.hcmut.emobility.fleet.dto;

import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** «DTO» filter of the fleet dashboard (UC-F08-01). */
public record FleetFilter(Long hubId, VehicleType type, VehicleStatus status) {

    /** True if vehicle v satisfies the selected Hub, type and status. */
    public boolean matches(Vehicle v) {
        if (hubId != null && !hubId.equals(v.getHubId())) return false;
        if (type != null && v.getType() != type) return false;
        return status == null || v.getStatus() == status;
    }

    /** True if no filter is selected. */
    public boolean isEmpty() { return hubId == null && type == null && status == null; }
}

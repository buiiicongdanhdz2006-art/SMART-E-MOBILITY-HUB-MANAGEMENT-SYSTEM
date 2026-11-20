package vn.edu.hcmut.emobility.common.api;

import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** Interface provided by Vehicle Management (F-03) to change vehicle state safely. */
public interface IVehicleState {
    Vehicle getVehicle(Long vehicleId);
    Vehicle findByCode(String code);
    /** Changes the status, respecting the allowed transitions of the vehicle state chart. */
    void changeStatus(Long vehicleId, VehicleStatus status);
    /** Manual correction by an Operator (UC-F08-05): no transition check. */
    void forceStatus(Long vehicleId, VehicleStatus status);
    /** Telemetry received from IoT (F-12). Null values are ignored. */
    Vehicle updateTelemetry(Long vehicleId, Integer batteryLevel, Double lat, Double lng);
}

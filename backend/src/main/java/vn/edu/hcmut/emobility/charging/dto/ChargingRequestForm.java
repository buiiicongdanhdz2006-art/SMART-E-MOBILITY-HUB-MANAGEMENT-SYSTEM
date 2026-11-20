package vn.edu.hcmut.emobility.charging.dto;

import java.time.LocalDateTime;

/**
 * «DTO» charging registration form (UC-F06-02). Exactly one of vehicleId (shared vehicle, Operator)
 * or personalVehicleId (personal vehicle, User) is given.
 */
public record ChargingRequestForm(Long vehicleId, Long personalVehicleId, Long hubId, String area,
                                  LocalDateTime startTime, Integer expectedDuration, Integer batteryLevel,
                                  Integer targetBattery, LocalDateTime nextUsageTime) {}

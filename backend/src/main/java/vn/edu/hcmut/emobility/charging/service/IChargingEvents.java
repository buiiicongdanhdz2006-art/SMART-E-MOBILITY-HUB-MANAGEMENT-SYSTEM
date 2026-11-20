package vn.edu.hcmut.emobility.charging.service;

import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.common.enums.PortStatus;

/**
 * «interface» IChargingEvents: events raised by ChargingService / IoTPortMonitor and handled by
 * SmartScheduler (F-07), so that F-06 does not depend on the scheduling algorithm.
 */
public interface IChargingEvents {
    /** A new request was created; reschedule if its priority is HIGH. */
    void onNewRequest(ChargingRequest request);

    /** A request was cancelled or expired; its port goes to the next request. */
    void onRequestReleased(ChargingRequest request);

    /** A charging session finished; the port goes to the next request. */
    void onChargingCompleted(ChargingRequest request);

    /** A port became FIXING or AVAILABLE again. */
    void portStatusChanged(ChargingPort port, PortStatus status);
}

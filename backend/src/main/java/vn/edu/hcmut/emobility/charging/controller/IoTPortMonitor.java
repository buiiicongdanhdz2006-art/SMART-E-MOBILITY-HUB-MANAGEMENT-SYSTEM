package vn.edu.hcmut.emobility.charging.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.service.ChargingService;
import vn.edu.hcmut.emobility.charging.service.IChargingEvents;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;

/**
 * «boundary» IoTPortMonitor - receives the events of the IoT Charging Simulator (through the MQTT
 * subscriber or the HTTP ingest endpoint of F-12) and forwards them to ChargingService / SmartScheduler.
 */
@Component
public class IoTPortMonitor {

    private static final Logger log = LoggerFactory.getLogger(IoTPortMonitor.class);

    private final ChargingPortRepository ports;
    private final ChargingService chargingService;
    private final IChargingEvents events;

    public IoTPortMonitor(ChargingPortRepository ports, ChargingService chargingService, IChargingEvents events) {
        this.ports = ports;
        this.chargingService = chargingService;
        this.events = events;
    }

    /** Vehicle plugged into the port: starts the charging session (UC-F06-04). */
    public void onVehiclePlugged(String portCode, String vehicleRef) {
        try {
            chargingService.startCharging(port(portCode).getId(), vehicleRef);
        } catch (ApiException | IllegalStateException e) {
            log.warn("Plug-in rejected on {}: {}", portCode, e.getMessage());
        }
    }

    /** Battery reading of the vehicle on the port (UC-F06-04). */
    public void onBatteryReading(String portCode, int level, Double voltage, Double current, Double temperature) {
        chargingService.handleBatteryUpdate(port(portCode).getId(), level, voltage, current, temperature);
    }

    /** Port status reported by the hardware (FIXING / AVAILABLE) -&gt; SmartScheduler.portStatusChanged (UC-F07-03). */
    @Transactional
    public void onPortStatus(String portCode, PortStatus status) {
        events.portStatusChanged(port(portCode), status);
    }

    /** The port stopped sending data (UC-F06-04). */
    public void onConnectionLost(String portCode) {
        chargingService.handleConnectionLost(port(portCode).getId());
    }

    private ChargingPort port(String code) {
        return ports.findByCode(code).orElseThrow(() -> ApiException.notFound("cổng sạc " + code));
    }
}

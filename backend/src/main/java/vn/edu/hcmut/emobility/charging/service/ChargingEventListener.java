package vn.edu.hcmut.emobility.charging.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.events.PortFaultEvent;
import vn.edu.hcmut.emobility.common.events.PortRepairedEvent;

/** Reacts to incident events of F-09 so that the schedule always uses the real port status. */
@Component
public class ChargingEventListener {

    private final ChargingPortRepository ports;
    private final IChargingEvents events;

    public ChargingEventListener(ChargingPortRepository ports, IChargingEvents events) {
        this.ports = ports;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void onPortFault(PortFaultEvent e) {
        ports.findById(e.portId()).ifPresent(p -> events.portStatusChanged(p, PortStatus.FIXING));
    }

    @EventListener
    @Transactional
    public void onPortRepaired(PortRepairedEvent e) {
        ports.findById(e.portId()).ifPresent(p -> events.portStatusChanged(p, PortStatus.AVAILABLE));
    }
}

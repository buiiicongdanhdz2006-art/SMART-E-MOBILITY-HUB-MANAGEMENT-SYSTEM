package vn.edu.hcmut.emobility.simulation.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.booking.repository.BookingRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.repository.ChargingRequestRepository;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.simulation.entity.HubState;
import vn.edu.hcmut.emobility.simulation.entity.SimulationError;
import vn.edu.hcmut.emobility.simulation.entity.TimeWindow;
import vn.edu.hcmut.emobility.simulation.repository.SimulationErrorRepository;

/** "HubDatabase" of the F-10 / F-11 class diagram: hub operational data and simulation errors. */
@Component
public class HubDatabase {

    private final IHubQuery hubQuery;
    private final BookingRepository bookings;
    private final ChargingRequestRepository chargingRequests;
    private final SimulationErrorRepository errors;

    public HubDatabase(IHubQuery hubQuery, BookingRepository bookings, ChargingRequestRepository chargingRequests,
                       SimulationErrorRepository errors) {
        this.hubQuery = hubQuery;
        this.bookings = bookings;
        this.chargingRequests = chargingRequests;
        this.errors = errors;
    }

    /** Operational data of the Hub (vehicles, bookings, charging ports) within the time window (F10-NF-05). */
    @Transactional(readOnly = true)
    public HubState getCurrentHubState(Long hubId, TimeWindow window) {
        Hub h = hubQuery.getHub(hubId);
        HubState s = new HubState();
        s.hubId = h.getId();
        s.hubName = h.getName();
        s.totalVehicles = h.getVehicles(null).size();
        s.availableVehicles = h.countAvailable();
        s.totalSlots = h.getParkingSlots().size();
        s.freeSlots = h.countFreeSlots();
        s.totalPorts = h.getChargingPorts().size();
        s.workingPorts = (int) h.countWorkingPorts();
        s.openHour = h.getOpenHour();
        s.closeHour = h.getCloseHour();

        List<ChargingRequest> active = chargingRequests.findByHub_IdAndStatusIn(hubId,
                List.of(RequestStatus.WAITING, RequestStatus.ASSIGNED, RequestStatus.CHARGING, RequestStatus.CREATED));
        s.chargingNow = (int) active.stream().filter(r -> r.getStatus() == RequestStatus.CHARGING).count();
        s.waitingRequests = h.waitingCount();
        double avgMinutes = active.stream().mapToInt(ChargingRequest::getExpectedDuration).average().orElse(60);
        s.portThroughputPerHour = 60.0 / Math.max(15, avgMinutes);

        LocalDateTime start = window != null && window.isValid() ? window.getStart() : LocalDateTime.now();
        LocalDateTime end = window != null && window.isValid() ? window.getEnd() : start.plusHours(1);
        s.activeBookings = (int) bookings.countActiveAtHub(hubId, start, end);

        // Peak-hour demand estimated from the configured demand and what is happening now.
        s.vehicleDemandPerHour = Math.max(1, h.getExpectedDemand() + s.activeBookings);
        s.chargingDemandPerHour = Math.max(1.0, active.size() * 0.8 + s.workingPorts * s.portThroughputPerHour * 0.5);
        return s;
    }

    /** Current data of the Hub for the Operator's dashboard (F11-NF-01). */
    public HubState getData(Long hubId) { return getCurrentHubState(hubId, null); }

    /** Records a simulation error with its source and message (F10-AF-02). */
    @Transactional
    public void logError(String source, String message) { errors.save(new SimulationError(source, message)); }
}

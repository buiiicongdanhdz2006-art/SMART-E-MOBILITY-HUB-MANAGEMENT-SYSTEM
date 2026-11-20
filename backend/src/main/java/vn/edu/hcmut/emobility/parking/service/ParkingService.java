package vn.edu.hcmut.emobility.parking.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.ReservationStatus;
import vn.edu.hcmut.emobility.common.enums.SlotStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.parking.entity.ParkingReservation;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;
import vn.edu.hcmut.emobility.parking.repository.ParkingReservationRepository;
import vn.edu.hcmut.emobility.parking.repository.ParkingSlotRepository;
import vn.edu.hcmut.emobility.parking.repository.PersonalVehicleRepository;

/** «control» ParkingService - F-05 Parking reservation (UC-F05-01 .. 05). */
@Service
public class ParkingService {

    private final ParkingSlotRepository slots;
    private final ParkingReservationRepository reservations;
    private final PersonalVehicleRepository personalVehicles;
    private final IHubQuery hubQuery;
    private final INotification notification;

    public ParkingService(ParkingSlotRepository slots, ParkingReservationRepository reservations,
                          PersonalVehicleRepository personalVehicles, IHubQuery hubQuery, INotification notification) {
        this.slots = slots;
        this.reservations = reservations;
        this.personalVehicles = personalVehicles;
        this.hubQuery = hubQuery;
        this.notification = notification;
    }

    /** UC-F05-01: slots of a Hub with their status. */
    @Transactional(readOnly = true)
    public List<ParkingSlot> viewParkingAvailability(Long hubId) {
        hubQuery.getHub(hubId);
        return slots.findByHubIdOrderByCode(hubId);
    }

    /** UC-F05-02: checks availability, assigns a slot, creates the reservation, slot -&gt; RESERVED. */
    @Transactional
    public ParkingReservation reserveSlot(User owner, Long vehicleId, Long hubId, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        List<String> errors = new ArrayList<>();
        if (start == null || end == null) errors.add("Thời gian bắt đầu và kết thúc là bắt buộc");
        else {
            if (start.isBefore(now.minusMinutes(5))) errors.add("Thời gian bắt đầu đã qua");
            if (!end.isAfter(start)) errors.add("Thời gian kết thúc phải sau thời gian bắt đầu");
            if (Duration.between(start, end).toHours() > 24) errors.add("Chỉ đặt chỗ tối đa 24 giờ");
        }
        if (vehicleId == null) errors.add("Chưa chọn xe cá nhân");
        if (!errors.isEmpty()) throw ApiException.validation(errors);

        PersonalVehicle vehicle = personalVehicles.findById(vehicleId).orElseThrow(() -> ApiException.notFound("xe cá nhân"));
        if (!vehicle.getOwnerId().equals(owner.getId())) throw ApiException.forbidden("Xe không thuộc về bạn");
        boolean vehicleBusy = reservations.findByOwnerIdOrderByStartTimeDesc(owner.getId()).stream()
                .anyMatch(r -> r.isActive() && r.getVehicle().getId().equals(vehicleId) && r.overlaps(start, end));
        if (vehicleBusy) throw ApiException.conflict("Xe " + vehicle.getPlate() + " đã có chỗ đỗ trong khung giờ này");

        Hub hub = hubQuery.getHub(hubId);
        if (!hub.isActive()) throw ApiException.conflict("Trạm đang tạm ngưng hoạt động");
        // Lock every slot of the Hub so that two students cannot get the same slot (no double-booking).
        ParkingSlot slot = slots.lockByHub(hubId).stream()
                .filter(s -> s.isAvailable(start, end))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("Trạm " + hub.getName() + " đã hết chỗ đỗ trong khung giờ này"));
        ParkingReservation r = reservations.save(new ParkingReservation(owner, vehicle, slot, start, end));
        slot.getReservations().add(r);
        slot.updateStatus(SlotStatus.RESERVED);
        return r;
    }

    /** UC-F05-03: checks the reservation is valid, records the check-in time, slot -&gt; OCCUPIED. */
    @Transactional
    public ParkingReservation checkIn(User owner, Long reservationId) {
        ParkingReservation r = own(owner, reservationId);
        LocalDateTime now = LocalDateTime.now();
        if (!r.isValid(now)) throw ApiException.conflict("Đặt chỗ không còn hiệu lực");
        if (now.isBefore(r.getStartTime().minusMinutes(30))) throw ApiException.conflict("Chỉ check-in trước giờ đặt tối đa 30 phút");
        r.checkIn(now);
        r.getSlot().updateStatus(SlotStatus.OCCUPIED);
        return r;
    }

    /** UC-F05-04: records the check-out time, completes the reservation, slot -&gt; AVAILABLE. */
    @Transactional
    public ParkingReservation checkOut(User owner, Long reservationId) {
        ParkingReservation r = own(owner, reservationId);
        if (r.getStatus() != ReservationStatus.CHECKED_IN) throw ApiException.conflict("Xe chưa check-in");
        r.checkOut(LocalDateTime.now());
        refreshSlotStatus(r.getSlot());
        return r;
    }

    /** UC-F05-05: cancels a reservation that is not checked in, slot -&gt; AVAILABLE. */
    @Transactional
    public ParkingReservation cancelReservation(User owner, Long reservationId) {
        ParkingReservation r = own(owner, reservationId);
        r.cancel();
        refreshSlotStatus(r.getSlot());
        return r;
    }

    @Transactional(readOnly = true)
    public List<ParkingReservation> myReservations(User owner) {
        return reservations.findByOwnerIdOrderByStartTimeDesc(owner.getId());
    }

    /** Slot sensor data (F-12): occupied by a car without check-in shows OCCUPIED. */
    @Transactional
    public ParkingSlot onSlotSensor(String slotCode, boolean occupied) {
        ParkingSlot slot = slots.findByCode(slotCode).orElseThrow(() -> ApiException.notFound("chỗ đỗ " + slotCode));
        slot.updateSensor(occupied);
        if (occupied) slot.updateStatus(SlotStatus.OCCUPIED);
        else refreshSlotStatus(slot);
        return slot;
    }

    /** RESERVED reservations not checked in within the grace time expire. */
    @Scheduled(fixedDelay = 60000, initialDelay = 40000)
    @Transactional
    public int expireReservations() {
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (ParkingReservation r : reservations.findByStatusIn(List.of(ReservationStatus.RESERVED))) {
            if (!r.isExpired(now)) continue;
            r.expire();
            refreshSlotStatus(r.getSlot());
            notification.notifyUser(r.getOwnerId(), AlertType.BOOKING_EXPIRING, "Đặt chỗ đỗ đã hết hạn",
                    "Chỗ " + r.getSlotCode() + " đã được giải phóng vì bạn chưa check-in.");
            n++;
        }
        return n;
    }

    /** OCCUPIED if a car is checked in (or the sensor sees a car), RESERVED if reserved, otherwise AVAILABLE. */
    void refreshSlotStatus(ParkingSlot slot) {
        List<ParkingReservation> active = reservations.findBySlotIdAndStatusIn(slot.getId(),
                List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN));
        if (active.stream().anyMatch(x -> x.getStatus() == ReservationStatus.CHECKED_IN) || Boolean.TRUE.equals(slot.getSensorOccupied())) {
            slot.updateStatus(SlotStatus.OCCUPIED);
        } else if (!active.isEmpty()) {
            slot.updateStatus(SlotStatus.RESERVED);
        } else {
            slot.updateStatus(SlotStatus.AVAILABLE);
        }
    }

    private ParkingReservation own(User owner, Long id) {
        ParkingReservation r = reservations.findById(id).orElseThrow(() -> ApiException.notFound("đặt chỗ"));
        if (!r.getOwnerId().equals(owner.getId())) throw ApiException.forbidden("Không phải đặt chỗ của bạn");
        return r;
    }
}

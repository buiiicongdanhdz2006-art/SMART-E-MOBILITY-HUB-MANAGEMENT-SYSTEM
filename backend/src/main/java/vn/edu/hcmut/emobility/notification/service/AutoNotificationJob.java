package vn.edu.hcmut.emobility.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.booking.repository.BookingRepository;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.BookingStatus;
import vn.edu.hcmut.emobility.common.enums.ReservationStatus;
import vn.edu.hcmut.emobility.parking.entity.ParkingReservation;
import vn.edu.hcmut.emobility.parking.repository.ParkingReservationRepository;

/**
 * F-13 non-interactive notifications: expiring bookings and parking reservations.
 * (Low battery is sent by IoTDataService, completed charging by ChargingService.)
 */
@Component
public class AutoNotificationJob {

    public static final int REMIND_MINUTES = 10;

    private final BookingRepository bookings;
    private final ParkingReservationRepository reservations;
    private final NotificationService notification;

    public AutoNotificationJob(BookingRepository bookings, ParkingReservationRepository reservations, NotificationService notification) {
        this.bookings = bookings;
        this.reservations = reservations;
        this.notification = notification;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 50000)
    @Transactional
    public void remind() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusMinutes(REMIND_MINUTES);
        for (Booking b : bookings.findByStatus(BookingStatus.RESERVED)) {
            if (!b.isPickupReminderSent() && !b.getPickupTime().isAfter(soon)) {
                b.setPickupReminderSent(true);
                notification.notifyUser(b.getUserId(), AlertType.BOOKING_EXPIRING, "Sắp đến giờ nhận xe",
                        "Xe " + b.getVehicleCode() + " tại " + b.getPickupHubName() + " được giữ đến "
                                + b.getPickupTime().plusMinutes(Booking.NO_SHOW_MINUTES).toLocalTime().withNano(0) + ".");
            }
        }
        for (Booking b : bookings.findByStatus(BookingStatus.IN_PROGRESS)) {
            if (!b.isReturnReminderSent() && !b.getReturnTime().isAfter(soon)) {
                b.setReturnReminderSent(true);
                notification.notifyUser(b.getUserId(), AlertType.BOOKING_EXPIRING, "Sắp hết giờ thuê xe",
                        "Vui lòng trả xe " + b.getVehicleCode() + " trước " + b.getReturnTime().toLocalTime().withNano(0) + ".");
            }
        }
        List<ParkingReservation> checkedIn = reservations.findByStatusIn(List.of(ReservationStatus.CHECKED_IN));
        for (ParkingReservation r : checkedIn) {
            if (!r.isReminderSent() && !r.getEndTime().isAfter(soon)) {
                r.setReminderSent(true);
                notification.notifyUser(r.getOwnerId(), AlertType.BOOKING_EXPIRING, "Sắp hết giờ đỗ xe",
                        "Chỗ " + r.getSlotCode() + " hết hạn lúc " + r.getEndTime().toLocalTime().withNano(0) + ".");
            }
        }
    }
}

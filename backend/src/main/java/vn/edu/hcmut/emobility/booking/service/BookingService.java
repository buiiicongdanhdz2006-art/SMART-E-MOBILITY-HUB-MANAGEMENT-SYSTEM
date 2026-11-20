package vn.edu.hcmut.emobility.booking.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.booking.entity.Rental;
import vn.edu.hcmut.emobility.booking.repository.BookingRepository;
import vn.edu.hcmut.emobility.booking.repository.RentalRepository;
import vn.edu.hcmut.emobility.common.api.IHubQuery;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.BookingStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/** «control» BookingService - F-04 Vehicle booking &amp; rental (UC-F04-01 .. 05). */
@Service
public class BookingService {

    public static final int DEFAULT_MIN_BATTERY = 20;
    public static final int MAX_BOOKING_HOURS = 24;
    public static final int MAX_ADVANCE_HOURS = 24;

    private final BookingRepository bookings;
    private final RentalRepository rentals;
    private final VehicleRepository vehicles;
    private final IHubQuery hubQuery;
    private final INotification notification;

    public BookingService(BookingRepository bookings, RentalRepository rentals, VehicleRepository vehicles,
                          IHubQuery hubQuery, INotification notification) {
        this.bookings = bookings;
        this.rentals = rentals;
        this.vehicles = vehicles;
        this.hubQuery = hubQuery;
        this.notification = notification;
    }

    /** UC-F04-01: available vehicles matching type, battery and location (origin Hub; destination must accept returns). */
    @Transactional(readOnly = true)
    public List<Vehicle> searchVehicles(VehicleType type, Long originHubId, Long destinationHubId, Integer minBattery) {
        int min = minBattery == null ? DEFAULT_MIN_BATTERY : minBattery;
        if (destinationHubId != null) {
            Hub dest = hubQuery.getHub(destinationHubId);
            LocalDateTime now = LocalDateTime.now();
            if (!dest.isActive() || dest.getAvailableSlots(now, now.plusHours(1)).isEmpty()) return List.of();
        }
        List<Hub> origins = originHubId == null ? hubQuery.getAllHubs() : List.of(hubQuery.getHub(originHubId));
        return origins.stream()
                .filter(Hub::isActive)
                .flatMap(h -> h.getAvailableVehicles().stream())
                .filter(v -> type == null || v.getType() == type)
                .filter(v -> v.hasEnoughBattery(min))
                .sorted(Comparator.comparingInt(Vehicle::getBatteryLevel).reversed())
                .toList();
    }

    /** UC-F04-02: checks availability, battery and time conflicts, creates the booking, vehicle -&gt; RESERVED. */
    @Transactional
    public Booking bookVehicle(User user, Long vehicleId, LocalDateTime pickupTime, LocalDateTime returnTime, Long destinationHubId) {
        LocalDateTime now = LocalDateTime.now();
        List<String> errors = new ArrayList<>();
        if (pickupTime == null || returnTime == null) errors.add("Thời gian nhận và trả xe là bắt buộc");
        else {
            if (pickupTime.isBefore(now.minusMinutes(5))) errors.add("Thời gian nhận xe đã qua");
            if (!returnTime.isAfter(pickupTime)) errors.add("Thời gian trả phải sau thời gian nhận");
            if (Duration.between(pickupTime, returnTime).toHours() > MAX_BOOKING_HOURS) errors.add("Thời gian thuê tối đa " + MAX_BOOKING_HOURS + " giờ");
            if (pickupTime.isAfter(now.plusHours(MAX_ADVANCE_HOURS))) errors.add("Chỉ đặt trước tối đa " + MAX_ADVANCE_HOURS + " giờ");
        }
        if (!errors.isEmpty()) throw ApiException.validation(errors);

        // Row lock on the vehicle: two students booking the same vehicle at the same time are serialised.
        Vehicle vehicle = vehicles.lockById(vehicleId).orElseThrow(() -> ApiException.notFound("xe #" + vehicleId));
        if (!vehicle.isAvailable()) throw ApiException.conflict("Xe " + vehicle.getCode() + " không còn sẵn sàng");
        if (!vehicle.hasEnoughBattery(DEFAULT_MIN_BATTERY)) throw ApiException.conflict("Pin xe thấp hơn " + DEFAULT_MIN_BATTERY + "%");
        if (bookings.countOverlapping(vehicleId, pickupTime, returnTime) > 0) throw ApiException.conflict("Xe đã được đặt trong khung giờ này");
        if (bookings.countUserOverlapping(user.getId(), pickupTime, returnTime) > 0) throw ApiException.conflict("Bạn đã có lượt đặt xe trùng khung giờ");

        Hub destination = destinationHubId == null ? null : hubQuery.getHub(destinationHubId);
        Booking booking = bookings.save(new Booking(user, vehicle, vehicle.getCurrentHub(), destination, pickupTime, returnTime));
        vehicle.updateStatus(VehicleStatus.RESERVED);
        vehicle.setNextUsageTime(pickupTime);
        notification.notifyUser(user.getId(), AlertType.GENERAL, "Đặt xe thành công",
                "Xe " + vehicle.getCode() + " đã được giữ đến " + pickupTime.plusMinutes(Booking.NO_SHOW_MINUTES).toLocalTime().withNano(0) + ".");
        return booking;
    }

    /** UC-F04-03: checks the booking, vehicle -&gt; IN_USE and starts a rental. */
    @Transactional
    public Rental pickUpVehicle(User user, Long bookingId) {
        Booking booking = ownBooking(user, bookingId);
        LocalDateTime now = LocalDateTime.now();
        if (booking.getStatus() != BookingStatus.RESERVED) throw ApiException.conflict("Lượt đặt đang " + booking.getStatus());
        if (now.isBefore(booking.getPickupTime().minusMinutes(15))) throw ApiException.conflict("Chỉ nhận xe trước giờ hẹn tối đa 15 phút");
        if (booking.isNoShow(now)) throw ApiException.conflict("Lượt đặt đã quá hạn nhận xe");
        Vehicle v = vehicles.lockById(booking.getVehicleId()).orElseThrow();
        v.updateStatus(VehicleStatus.IN_USE);
        v.setNextUsageTime(null);
        v.leaveHub();
        booking.startUse();
        Rental rental = new Rental(booking);
        rental.start();
        return rentals.save(rental);
    }

    /** UC-F04-04: checks the Hub has a free slot, ends the rental, vehicle -&gt; AVAILABLE at that Hub. */
    @Transactional
    public Rental returnVehicle(User user, Long rentalId, Long hubId) {
        Rental rental = rentals.findById(rentalId).orElseThrow(() -> ApiException.notFound("lượt thuê"));
        if (!rental.getBooking().getUserId().equals(user.getId())) throw ApiException.forbidden("Không phải lượt thuê của bạn");
        if (rental.isEnded()) throw ApiException.conflict("Lượt thuê đã kết thúc");
        Hub hub = hubQuery.getHub(hubId);
        LocalDateTime now = LocalDateTime.now();
        if (!hub.isActive() || hub.getAvailableSlots(now, now.plusMinutes(30)).isEmpty()) {
            throw ApiException.conflict("Trạm " + hub.getName() + " đã hết chỗ trả xe, vui lòng chọn trạm khác");
        }
        rental.end(hub);
        Vehicle v = rental.getBooking().getVehicle();
        v.moveToHub(hub);
        v.updateStatus(VehicleStatus.AVAILABLE);
        rental.getBooking().complete();
        if (v.getBatteryLevel() < 20) {
            notification.notifyOperators(AlertType.LOW_BATTERY, "Xe pin thấp",
                    "Xe " + v.getCode() + " được trả tại " + hub.getName() + " với pin " + v.getBatteryLevel() + "%.");
        }
        return rental;
    }

    /** UC-F04-05: cancels the booking if allowed, vehicle back to AVAILABLE. */
    @Transactional
    public Booking cancelBooking(User user, Long bookingId) {
        Booking booking = ownBooking(user, bookingId);
        if (!booking.canCancel(LocalDateTime.now())) throw ApiException.conflict("Chỉ huỷ được lượt đặt chưa đến giờ nhận xe");
        booking.cancel();
        Vehicle v = booking.getVehicle();
        if (v.getStatus() == VehicleStatus.RESERVED) v.updateStatus(VehicleStatus.AVAILABLE);
        v.setNextUsageTime(null);
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> myBookings(User user) { return bookings.findByUserIdOrderByCreatedAtDesc(user.getId()); }

    @Transactional(readOnly = true)
    public Rental rentalOfBooking(User user, Long bookingId) {
        ownBooking(user, bookingId);
        return rentals.findByBookingId(bookingId).orElseThrow(() -> ApiException.notFound("lượt thuê"));
    }

    /** Bookings not picked up 15 minutes after the pickup time expire and release the vehicle. */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    @Transactional
    public int expireNoShows() {
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (Booking b : bookings.findByStatus(BookingStatus.RESERVED)) {
            if (!b.isNoShow(now)) continue;
            b.expire();
            Vehicle v = b.getVehicle();
            if (v.getStatus() == VehicleStatus.RESERVED) v.updateStatus(VehicleStatus.AVAILABLE);
            v.setNextUsageTime(null);
            notification.notifyUser(b.getUserId(), AlertType.BOOKING_EXPIRING, "Lượt đặt xe đã hết hạn",
                    "Bạn không nhận xe " + v.getCode() + " trong " + Booking.NO_SHOW_MINUTES + " phút nên lượt đặt đã bị huỷ.");
            n++;
        }
        return n;
    }

    private Booking ownBooking(User user, Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("lượt đặt xe"));
        if (!b.getUserId().equals(user.getId())) throw ApiException.forbidden("Không phải lượt đặt xe của bạn");
        return b;
    }
}

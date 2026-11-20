package vn.edu.hcmut.emobility.booking.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.booking.entity.Rental;
import vn.edu.hcmut.emobility.booking.service.BookingService;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** REST boundary of F-04. */
@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings/search")
    public List<Vehicle> searchVehicles(HttpServletRequest request,
                                        @RequestParam(required = false) VehicleType type,
                                        @RequestParam(required = false) Long originHubId,
                                        @RequestParam(required = false) Long destinationHubId,
                                        @RequestParam(required = false) Integer minBattery) {
        CurrentAccount.require(request);
        return bookingService.searchVehicles(type, originHubId, destinationHubId, minBattery);
    }

    @PostMapping("/bookings")
    public Booking bookVehicle(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = CurrentAccount.requireUser(request);
        if (body.get("vehicleId") == null) throw ApiException.badRequest("Chưa chọn xe");
        return bookingService.bookVehicle(u, Long.valueOf(body.get("vehicleId")), time(body.get("pickupTime")),
                time(body.get("returnTime")), body.get("destinationHubId") == null || body.get("destinationHubId").isBlank()
                        ? null : Long.valueOf(body.get("destinationHubId")));
    }

    @GetMapping("/bookings/my")
    public List<Booking> myBookings(HttpServletRequest request) {
        return bookingService.myBookings(CurrentAccount.requireUser(request));
    }

    @PostMapping("/bookings/{bookingId}/pickup")
    public Rental pickUpVehicle(HttpServletRequest request, @PathVariable Long bookingId) {
        return bookingService.pickUpVehicle(CurrentAccount.requireUser(request), bookingId);
    }

    @GetMapping("/bookings/{bookingId}/rental")
    public Rental rentalOfBooking(HttpServletRequest request, @PathVariable Long bookingId) {
        return bookingService.rentalOfBooking(CurrentAccount.requireUser(request), bookingId);
    }

    @PostMapping("/rentals/{rentalId}/return")
    public Rental returnVehicle(HttpServletRequest request, @PathVariable Long rentalId, @RequestBody Map<String, Long> body) {
        if (body.get("hubId") == null) throw ApiException.badRequest("Chưa chọn trạm trả xe");
        return bookingService.returnVehicle(CurrentAccount.requireUser(request), rentalId, body.get("hubId"));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public Booking cancelBooking(HttpServletRequest request, @PathVariable Long bookingId) {
        return bookingService.cancelBooking(CurrentAccount.requireUser(request), bookingId);
    }

    static LocalDateTime time(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            throw ApiException.badRequest("Thời gian không hợp lệ: " + s);
        }
    }
}

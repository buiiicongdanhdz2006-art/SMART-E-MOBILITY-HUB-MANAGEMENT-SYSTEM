package vn.edu.hcmut.emobility.parking.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.parking.entity.ParkingReservation;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.parking.service.ParkingService;

/** REST boundary of F-05. */
@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService parkingService;

    public ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @GetMapping("/hubs/{hubId}/slots")
    public List<ParkingSlot> viewParkingAvailability(@PathVariable Long hubId) {
        return parkingService.viewParkingAvailability(hubId);
    }

    @PostMapping("/reservations")
    public ParkingReservation reserveSlot(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = CurrentAccount.requireUser(request);
        return parkingService.reserveSlot(u, id(body.get("vehicleId")), id(body.get("hubId")),
                time(body.get("startTime")), time(body.get("endTime")));
    }

    @GetMapping("/reservations/my")
    public List<ParkingReservation> myReservations(HttpServletRequest request) {
        return parkingService.myReservations(CurrentAccount.requireUser(request));
    }

    @PostMapping("/reservations/{id}/check-in")
    public ParkingReservation checkIn(HttpServletRequest request, @PathVariable Long id) {
        return parkingService.checkIn(CurrentAccount.requireUser(request), id);
    }

    @PostMapping("/reservations/{id}/check-out")
    public ParkingReservation checkOut(HttpServletRequest request, @PathVariable Long id) {
        return parkingService.checkOut(CurrentAccount.requireUser(request), id);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ParkingReservation cancel(HttpServletRequest request, @PathVariable Long id) {
        return parkingService.cancelReservation(CurrentAccount.requireUser(request), id);
    }

    private static Long id(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.valueOf(s); } catch (NumberFormatException e) { throw ApiException.badRequest("Mã không hợp lệ: " + s); }
    }

    private static LocalDateTime time(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); } catch (Exception e) { throw ApiException.badRequest("Thời gian không hợp lệ: " + s); }
    }
}

package vn.edu.hcmut.emobility.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.service.ProfileService;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;

/** «control» ProfileController - UC-F01-05 .. UC-F01-07. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileService.ProfileData getProfile(HttpServletRequest request) {
        return profiles.getProfile(CurrentAccount.require(request));
    }

    @PutMapping
    public ProfileService.ProfileData updateProfile(HttpServletRequest request, @RequestBody Map<String, String> data) {
        Account a = CurrentAccount.require(request);
        return profiles.updateProfile(a, data);
    }

    @GetMapping("/history")
    public List<Booking> getHistory(HttpServletRequest request, @RequestParam(defaultValue = "30") int days) {
        User u = CurrentAccount.requireUser(request);
        return profiles.getHistory(u.getId(), days);
    }

    @GetMapping("/bookings/{bookingId}")
    public Booking getBookingDetail(HttpServletRequest request, @PathVariable Long bookingId) {
        User u = CurrentAccount.requireUser(request);
        return profiles.getBookingDetail(u.getId(), bookingId);
    }
}

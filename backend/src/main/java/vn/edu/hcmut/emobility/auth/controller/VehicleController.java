package vn.edu.hcmut.emobility.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.service.ProfileService;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;

/** «control» VehicleController of F-01 - registers the personal vehicles of a student (UC-F01-08). */
@RestController
@RequestMapping("/api/profile/vehicles")
public class VehicleController {

    private final ProfileService profiles;

    public VehicleController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PostMapping
    public PersonalVehicle registerVehicle(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = CurrentAccount.requireUser(request);
        VehicleType type;
        try {
            type = body.get("type") == null ? null : VehicleType.valueOf(body.get("type"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Loại xe không hợp lệ");
        }
        return profiles.registerVehicle(u.getId(), type, body.get("plate"), body.get("brand"));
    }

    @GetMapping
    public List<PersonalVehicle> myVehicles(HttpServletRequest request) {
        return profiles.getVehicles(CurrentAccount.requireUser(request).getId());
    }
}

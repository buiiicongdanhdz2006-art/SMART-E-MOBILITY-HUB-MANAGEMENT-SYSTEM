package vn.edu.hcmut.emobility.vehicle.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.service.VehicleService;

/** REST boundary of F-03 (UC-F03-01 .. 03). */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleManagementController {

    private final VehicleService vehicleService;

    public VehicleManagementController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<Vehicle> viewVehicleList(HttpServletRequest request, @RequestParam(required = false) VehicleType type) {
        CurrentAccount.require(request);
        return vehicleService.viewVehicleList(type);
    }

    @GetMapping("/{vehicleId}")
    public Vehicle viewVehicleStatus(HttpServletRequest request, @PathVariable Long vehicleId) {
        CurrentAccount.require(request);
        return vehicleService.viewVehicleStatus(vehicleId);
    }

    @PostMapping
    public Vehicle create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        CurrentAccount.require(request, Role.ADMIN);
        return vehicleService.updateVehicleInfo(null, body);
    }

    @PutMapping("/{vehicleId}")
    public Vehicle update(HttpServletRequest request, @PathVariable Long vehicleId, @RequestBody Map<String, Object> body) {
        CurrentAccount.require(request, Role.ADMIN);
        return vehicleService.updateVehicleInfo(vehicleId, body);
    }
}

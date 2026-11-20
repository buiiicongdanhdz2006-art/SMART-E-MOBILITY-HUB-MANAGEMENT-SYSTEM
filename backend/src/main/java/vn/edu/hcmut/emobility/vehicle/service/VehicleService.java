package vn.edu.hcmut.emobility.vehicle.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.common.api.IVehicleState;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/** «control» VehicleService - F-03 Vehicle management; provides IVehicleState. */
@Service
public class VehicleService implements IVehicleState {

    private final VehicleRepository vehicles;
    private final HubRepository hubs;

    public VehicleService(VehicleRepository vehicles, HubRepository hubs) {
        this.vehicles = vehicles;
        this.hubs = hubs;
    }

    /** UC-F03-01: vehicle list filtered by type (both types if none is chosen). */
    @Transactional(readOnly = true)
    public List<Vehicle> viewVehicleList(VehicleType type) {
        return type == null ? vehicles.findAllByOrderByCode() : vehicles.findByTypeOrderByCode(type);
    }

    /** UC-F03-02: battery level, location and status of a vehicle. */
    @Transactional(readOnly = true)
    public Vehicle viewVehicleStatus(Long vehicleId) { return getVehicle(vehicleId); }

    /**
     * UC-F03-03: validates and saves new or updated vehicle information; rejects invalid data or duplicate IDs.
     * id == null creates a new vehicle.
     */
    @Transactional
    public Vehicle updateVehicleInfo(Long id, Map<String, Object> data) {
        List<String> errors = new ArrayList<>();
        String code = str(data.get("code"));
        VehicleType type = null;
        try {
            if (data.get("type") != null) type = VehicleType.valueOf(str(data.get("type")));
        } catch (IllegalArgumentException e) {
            errors.add("Loại xe không hợp lệ");
        }
        Integer battery = null;
        if (data.get("batteryLevel") != null) {
            try {
                battery = (int) Double.parseDouble(data.get("batteryLevel").toString());
                if (battery < 0 || battery > 100) errors.add("Mức pin phải trong khoảng 0-100");
            } catch (NumberFormatException e) {
                errors.add("Mức pin không hợp lệ");
            }
        }
        Hub hub = null;
        if (data.get("hubId") != null) {
            hub = hubs.findById(Long.valueOf(data.get("hubId").toString())).orElse(null);
            if (hub == null) errors.add("Trạm không tồn tại");
        }
        if (id == null) {
            if (code == null) errors.add("Mã xe là bắt buộc");
            if (type == null) errors.add("Loại xe là bắt buộc");
            if (hub == null) errors.add("Trạm là bắt buộc");
        }
        if (!errors.isEmpty()) throw ApiException.validation(errors);

        if (id == null) {
            if (vehicles.findByCode(code).isPresent()) throw ApiException.conflict("Mã xe " + code + " đã tồn tại");
            Vehicle v = new Vehicle(code, type, str(data.get("model")), hub, battery == null ? 100 : battery);
            return vehicles.save(v);
        }
        Vehicle v = getVehicle(id);
        if (code != null && !code.equals(v.getCode())) throw ApiException.badRequest("Không được đổi mã xe");
        if (type != null) v.setType(type);
        if (data.containsKey("model")) v.setModel(str(data.get("model")));
        if (battery != null) v.updateBattery(battery);
        if (hub != null) v.moveToHub(hub);
        if (data.get("status") != null) v.updateStatus(VehicleStatus.valueOf(str(data.get("status"))));
        return vehicles.save(v);
    }

    // ---- IVehicleState -------------------------------------------------------------------

    @Override
    public Vehicle getVehicle(Long vehicleId) {
        return vehicles.findById(vehicleId).orElseThrow(() -> ApiException.notFound("xe #" + vehicleId));
    }

    @Override
    public Vehicle findByCode(String code) {
        return vehicles.findByCode(code).orElse(null);
    }

    @Override
    @Transactional
    public void changeStatus(Long vehicleId, VehicleStatus status) {
        Vehicle v = getVehicle(vehicleId);
        v.updateStatus(status);
        vehicles.save(v);
    }

    @Override
    @Transactional
    public void forceStatus(Long vehicleId, VehicleStatus status) {
        Vehicle v = getVehicle(vehicleId);
        v.forceStatus(status);
        vehicles.save(v);
    }

    @Override
    @Transactional
    public Vehicle updateTelemetry(Long vehicleId, Integer batteryLevel, Double lat, Double lng) {
        Vehicle v = getVehicle(vehicleId);
        if (batteryLevel != null) v.updateBattery(batteryLevel);
        if (lat != null && lng != null) v.updateLocation(lat, lng);
        return vehicles.save(v);
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString().trim();
    }
}

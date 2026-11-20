package vn.edu.hcmut.emobility.vehicle.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByCode(String code);

    List<Vehicle> findByTypeOrderByCode(VehicleType type);

    List<Vehicle> findAllByOrderByCode();

    List<Vehicle> findByStatus(VehicleStatus status);

    /** VehicleRepository.findAvailableByHub(hubId) (F-08). */
    @Query("select v from Vehicle v where v.currentHub.id = :hubId and v.status = vn.edu.hcmut.emobility.common.enums.VehicleStatus.AVAILABLE order by v.batteryLevel desc")
    List<Vehicle> findAvailableByHub(@Param("hubId") Long hubId);

    /** Row lock used when booking / dispatching, so that two requests cannot take the same vehicle. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> lockById(@Param("id") Long id);
}

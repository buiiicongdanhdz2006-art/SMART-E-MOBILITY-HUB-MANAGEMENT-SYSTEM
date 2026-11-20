package vn.edu.hcmut.emobility.parking.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;

public interface PersonalVehicleRepository extends JpaRepository<PersonalVehicle, Long> {

    /** User.existsPlate(plate). */
    boolean existsByPlateIgnoreCase(String plate);

    List<PersonalVehicle> findByOwner_Id(Long ownerId);

    Optional<PersonalVehicle> findByPlateIgnoreCase(String plate);
}

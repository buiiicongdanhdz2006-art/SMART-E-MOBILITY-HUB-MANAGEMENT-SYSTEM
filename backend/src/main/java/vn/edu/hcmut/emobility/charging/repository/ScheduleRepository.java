package vn.edu.hcmut.emobility.charging.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingSchedule;

public interface ScheduleRepository extends JpaRepository<ChargingSchedule, Long> {
    Optional<ChargingSchedule> findFirstByHub_IdOrderByCreatedAtDesc(Long hubId);

    /** findLatestByHub(hubId) of the class diagram: the most recent schedule of the Hub. */
    default Optional<ChargingSchedule> findLatestByHub(Long hubId) {
        return findFirstByHub_IdOrderByCreatedAtDesc(hubId);
    }
}

package vn.edu.hcmut.emobility.charging.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingSchedule;

public interface ScheduleRepository extends JpaRepository<ChargingSchedule, Long> {
    /** findLatestByHub(hubId). */
    Optional<ChargingSchedule> findFirstByHubIdOrderByCreatedAtDesc(Long hubId);
}

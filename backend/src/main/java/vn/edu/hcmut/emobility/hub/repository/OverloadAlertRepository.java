package vn.edu.hcmut.emobility.hub.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.common.enums.AlertStatus;
import vn.edu.hcmut.emobility.hub.entity.OverloadAlert;

public interface OverloadAlertRepository extends JpaRepository<OverloadAlert, Long> {
    /** OverloadAlert.findByStatus(status): newest first. */
    List<OverloadAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    boolean existsByHubIdAndStatus(Long hubId, AlertStatus status);
}

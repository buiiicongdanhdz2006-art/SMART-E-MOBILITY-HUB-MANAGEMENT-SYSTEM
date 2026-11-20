package vn.edu.hcmut.emobility.notification.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.notification.entity.AlertFailureLog;

public interface AlertFailureLogRepository extends JpaRepository<AlertFailureLog, Long> {
    List<AlertFailureLog> findByAlertIdOrderByAtDesc(Long alertId);
}

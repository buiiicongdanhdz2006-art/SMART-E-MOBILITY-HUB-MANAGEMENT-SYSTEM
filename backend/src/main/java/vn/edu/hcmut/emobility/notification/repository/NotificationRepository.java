package vn.edu.hcmut.emobility.notification.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.recipientId = :accountId "
            + "or (n.recipientId is null and n.recipientRole = :role) order by n.createdAt desc")
    List<Notification> findForAccount(@Param("accountId") Long accountId, @Param("role") Role role);
}

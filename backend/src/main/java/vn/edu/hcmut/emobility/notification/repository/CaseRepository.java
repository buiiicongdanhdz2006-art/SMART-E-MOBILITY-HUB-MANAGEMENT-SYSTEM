package vn.edu.hcmut.emobility.notification.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmut.emobility.notification.entity.Case;
import vn.edu.hcmut.emobility.notification.entity.Complaint;

public interface CaseRepository extends JpaRepository<Case, Long> {

    List<Case> findAllByOrderByCreatedAtDesc();

    @Query("select c from Complaint c where c.relatedUserId = :userId order by c.createdAt desc")
    List<Complaint> findComplaintsByUser(@Param("userId") Long userId);
}

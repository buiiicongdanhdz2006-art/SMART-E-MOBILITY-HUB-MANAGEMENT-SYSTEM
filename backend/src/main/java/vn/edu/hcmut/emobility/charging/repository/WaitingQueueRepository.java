package vn.edu.hcmut.emobility.charging.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;

public interface WaitingQueueRepository extends JpaRepository<WaitingQueue, Long> {
    Optional<WaitingQueue> findByHub_Id(Long hubId);
}

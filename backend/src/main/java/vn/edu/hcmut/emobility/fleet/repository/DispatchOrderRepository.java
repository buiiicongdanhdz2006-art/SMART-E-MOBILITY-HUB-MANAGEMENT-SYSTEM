package vn.edu.hcmut.emobility.fleet.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.common.enums.DispatchStatus;
import vn.edu.hcmut.emobility.fleet.entity.DispatchOrder;

public interface DispatchOrderRepository extends JpaRepository<DispatchOrder, Long> {

    List<DispatchOrder> findByStatusInOrderByCreatedAtDesc(Collection<DispatchStatus> statuses);

    /** Orders that are not completed yet. */
    default List<DispatchOrder> findActive() {
        return findByStatusInOrderByCreatedAtDesc(List.of(DispatchStatus.PENDING, DispatchStatus.IN_PROGRESS));
    }
}

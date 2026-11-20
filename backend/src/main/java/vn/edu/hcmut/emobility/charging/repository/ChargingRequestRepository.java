package vn.edu.hcmut.emobility.charging.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;

public interface ChargingRequestRepository extends JpaRepository<ChargingRequest, Long> {

    /** findByUser(userId). */
    List<ChargingRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ChargingRequest> findByHubIdAndStatusIn(Long hubId, Collection<RequestStatus> statuses);

    List<ChargingRequest> findByStatus(RequestStatus status);

    Optional<ChargingRequest> findFirstByAssignedPortIdAndStatusIn(Long portId, Collection<RequestStatus> statuses);

    /** Requests of the Hub that are not charging yet (CREATED, WAITING, ASSIGNED). */
    default List<ChargingRequest> findPendingByHub(Long hubId) {
        return findByHubIdAndStatusIn(hubId, List.of(RequestStatus.CREATED, RequestStatus.WAITING, RequestStatus.ASSIGNED));
    }

    /** ASSIGNED requests whose vehicle was not plugged in within 15 minutes. */
    default List<ChargingRequest> findOverdue(LocalDateTime now) {
        return findByStatus(RequestStatus.ASSIGNED).stream().filter(r -> r.isOverdue(now)).toList();
    }

    /** The request currently using (CHARGING) or holding (ASSIGNED) a port. */
    default Optional<ChargingRequest> findActiveByPort(Long portId) {
        return findFirstByAssignedPortIdAndStatusIn(portId, List.of(RequestStatus.ASSIGNED, RequestStatus.CHARGING));
    }
}

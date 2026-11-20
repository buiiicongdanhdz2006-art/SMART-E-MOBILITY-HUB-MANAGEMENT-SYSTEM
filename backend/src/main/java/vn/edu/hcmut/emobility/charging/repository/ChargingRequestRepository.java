package vn.edu.hcmut.emobility.charging.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;

public interface ChargingRequestRepository extends JpaRepository<ChargingRequest, Long> {

    List<ChargingRequest> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** findByUser(userId) of the class diagram: requests of the user, newest first. */
    default List<ChargingRequest> findByUser(Long userId) {
        return findByUser_IdOrderByCreatedAtDesc(userId);
    }

    List<ChargingRequest> findByHub_IdAndStatusIn(Long hubId, Collection<RequestStatus> statuses);

    List<ChargingRequest> findByStatus(RequestStatus status);

    List<ChargingRequest> findByStatusIn(Collection<RequestStatus> statuses);

    Optional<ChargingRequest> findFirstByAssignedPort_IdAndStatusIn(Long portId, Collection<RequestStatus> statuses);

    /** Requests of the Hub that are not charging yet (CREATED, WAITING, ASSIGNED). */
    default List<ChargingRequest> findPendingByHub(Long hubId) {
        return findByHub_IdAndStatusIn(hubId, List.of(RequestStatus.CREATED, RequestStatus.WAITING, RequestStatus.ASSIGNED));
    }

    /** ASSIGNED requests whose vehicle was not plugged in within 15 minutes. */
    default List<ChargingRequest> findOverdue(LocalDateTime now) {
        return findByStatus(RequestStatus.ASSIGNED).stream().filter(r -> r.isOverdue(now)).toList();
    }

    /** The request currently using (CHARGING) or holding (ASSIGNED) a port. */
    default Optional<ChargingRequest> findActiveByPort(Long portId) {
        return findFirstByAssignedPort_IdAndStatusIn(portId, List.of(RequestStatus.ASSIGNED, RequestStatus.CHARGING));
    }
}

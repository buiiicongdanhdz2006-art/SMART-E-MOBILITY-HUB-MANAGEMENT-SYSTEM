package vn.edu.hcmut.emobility.charging.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;

public interface ChargingPortRepository extends JpaRepository<ChargingPort, Long> {

    /** findByHub(hubId): all ports of the Hub with their status. */
    List<ChargingPort> findByHub_IdOrderByCode(Long hubId);

    Optional<ChargingPort> findByCode(String code);

    /** Locks the ports of a Hub while ports are allocated, so one port is never given twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChargingPort p where p.hub.id = :hubId order by p.code")
    List<ChargingPort> lockByHub(@Param("hubId") Long hubId);

    /**
     * AVAILABLE ports of the area that are not assigned to another request. A port holds at most one
     * request at a time, so a free port is free for the whole [start, end] window.
     */
    default List<ChargingPort> findAvailable(Long hubId, String area, LocalDateTime start, LocalDateTime end) {
        return lockByHub(hubId).stream()
                .filter(ChargingPort::isFree)
                .filter(p -> area == null || area.isBlank() || area.equalsIgnoreCase(p.getArea()))
                .toList();
    }
}

package vn.edu.hcmut.emobility.parking.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {

    List<ParkingSlot> findByHub_IdOrderByCode(Long hubId);

    Optional<ParkingSlot> findByCode(String code);

    /** Locks every slot of the Hub while a reservation is made, preventing double-booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSlot s where s.hub.id = :hubId order by s.code")
    List<ParkingSlot> lockByHub(@Param("hubId") Long hubId);
}

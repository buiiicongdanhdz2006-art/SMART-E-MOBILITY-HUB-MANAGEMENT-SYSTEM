package vn.edu.hcmut.emobility.parking.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.common.enums.ReservationStatus;
import vn.edu.hcmut.emobility.parking.entity.ParkingReservation;

public interface ParkingReservationRepository extends JpaRepository<ParkingReservation, Long> {

    List<ParkingReservation> findByOwnerIdOrderByStartTimeDesc(Long ownerId);

    List<ParkingReservation> findByStatusIn(Collection<ReservationStatus> statuses);

    List<ParkingReservation> findBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);
}

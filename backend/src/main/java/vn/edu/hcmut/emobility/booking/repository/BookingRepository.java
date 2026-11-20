package vn.edu.hcmut.emobility.booking.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.common.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdAndStatusInOrderByPickupTimeAsc(Long userId, Collection<BookingStatus> statuses);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByStatus(BookingStatus status);

    /** Bookings of the vehicle that overlap [start, end] and are still active (no double-booking). */
    @Query("select count(b) from Booking b where b.vehicle.id = :vehicleId "
            + "and b.status in (vn.edu.hcmut.emobility.common.enums.BookingStatus.RESERVED, vn.edu.hcmut.emobility.common.enums.BookingStatus.IN_PROGRESS) "
            + "and b.pickupTime < :end and b.returnTime > :start")
    long countOverlapping(@Param("vehicleId") Long vehicleId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Active bookings of a user that overlap [start, end]: one user cannot ride two vehicles at the same time. */
    @Query("select count(b) from Booking b where b.user.id = :userId "
            + "and b.status in (vn.edu.hcmut.emobility.common.enums.BookingStatus.RESERVED, vn.edu.hcmut.emobility.common.enums.BookingStatus.IN_PROGRESS) "
            + "and b.pickupTime < :end and b.returnTime > :start")
    long countUserOverlapping(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Bookings starting at a Hub inside a time window (used by the what-if simulation, F-10). */
    @Query("select count(b) from Booking b where b.pickupHub.id = :hubId "
            + "and b.status in (vn.edu.hcmut.emobility.common.enums.BookingStatus.RESERVED, vn.edu.hcmut.emobility.common.enums.BookingStatus.IN_PROGRESS) "
            + "and b.pickupTime < :end and b.returnTime > :start")
    long countActiveAtHub(@Param("hubId") Long hubId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

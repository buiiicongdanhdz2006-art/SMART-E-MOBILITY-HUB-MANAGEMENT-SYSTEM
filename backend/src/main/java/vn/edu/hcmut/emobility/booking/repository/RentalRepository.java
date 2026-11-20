package vn.edu.hcmut.emobility.booking.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.booking.entity.Rental;

public interface RentalRepository extends JpaRepository<Rental, Long> {
    Optional<Rental> findByBooking_Id(Long bookingId);

    List<Rental> findByEndTimeIsNull();
}

package vn.edu.hcmut.emobility.hub.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.hub.entity.Hub;

public interface HubRepository extends JpaRepository<Hub, Long> {
    Optional<Hub> findByCode(String code);
}

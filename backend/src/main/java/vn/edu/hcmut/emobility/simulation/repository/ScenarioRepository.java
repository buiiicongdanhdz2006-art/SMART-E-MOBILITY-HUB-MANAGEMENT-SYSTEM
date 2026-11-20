package vn.edu.hcmut.emobility.simulation.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.simulation.entity.Scenario;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {
    Optional<Scenario> findFirstByCreatedByOrderByCreatedAtDesc(Long createdBy);

    List<Scenario> findTop20ByOrderByCreatedAtDesc();
}

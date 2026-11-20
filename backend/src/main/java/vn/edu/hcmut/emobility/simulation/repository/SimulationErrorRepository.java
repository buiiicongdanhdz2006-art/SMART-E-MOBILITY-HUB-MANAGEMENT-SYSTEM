package vn.edu.hcmut.emobility.simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.simulation.entity.SimulationError;

public interface SimulationErrorRepository extends JpaRepository<SimulationError, Long> {
}

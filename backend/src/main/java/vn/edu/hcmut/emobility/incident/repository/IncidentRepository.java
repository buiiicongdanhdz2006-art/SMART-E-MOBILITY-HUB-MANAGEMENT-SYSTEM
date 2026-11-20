package vn.edu.hcmut.emobility.incident.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.incident.dto.IncidentFilter;
import vn.edu.hcmut.emobility.incident.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findAllByOrderByCreatedAtDesc();

    /** Incidents matching the filter, newest first, with their history. */
    default List<Incident> findByFilter(IncidentFilter f) {
        return findAllByOrderByCreatedAtDesc().stream().filter(f::matches).toList();
    }
}

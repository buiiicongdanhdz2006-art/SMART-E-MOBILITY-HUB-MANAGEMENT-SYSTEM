package vn.edu.hcmut.emobility.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmut.emobility.auth.entity.Session;

public interface SessionRepository extends JpaRepository<Session, String> {
}

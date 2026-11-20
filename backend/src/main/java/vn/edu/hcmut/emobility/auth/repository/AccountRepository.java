package vn.edu.hcmut.emobility.auth.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Operator;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmailIgnoreCase(String email);

    Optional<Account> findByStudentId(String studentId);

    Optional<Account> findByPhone(String phone);

    @Query("select o from Operator o")
    List<Operator> findAllOperators();

    /** Account.find(id): account whose email or Student ID equals id. */
    default Optional<Account> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String v = id.trim();
        Optional<Account> byEmail = findByEmailIgnoreCase(v);
        return byEmail.isPresent() ? byEmail : findByStudentId(v);
    }

    /** Account.existsPhone(phone, exceptId). */
    default boolean existsPhone(String phone, Long exceptId) {
        return findByPhone(phone).filter(a -> !a.getId().equals(exceptId)).isPresent();
    }
}

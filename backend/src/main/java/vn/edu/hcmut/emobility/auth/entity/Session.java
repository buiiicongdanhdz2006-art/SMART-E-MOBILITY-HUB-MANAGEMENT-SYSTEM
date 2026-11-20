package vn.edu.hcmut.emobility.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** Login session identified by a random token (UC-F01-02, UC-F01-03). */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    private String token;
    private Long accountId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    protected Session() {}

    public static Session create(Long accountId, int hours) {
        Session s = new Session();
        s.token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        s.accountId = accountId;
        s.createdAt = LocalDateTime.now();
        s.expiresAt = s.createdAt.plusHours(hours);
        return s;
    }

    public boolean isExpired(LocalDateTime now) { return now.isAfter(expiresAt); }

    public String getToken() { return token; }
    public Long getAccountId() { return accountId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}

package vn.edu.hcmut.emobility.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import vn.edu.hcmut.emobility.common.enums.AccountStatus;
import vn.edu.hcmut.emobility.common.enums.Role;

/** Abstract parent of User, Operator and Admin (actors of the use-case diagram). */
@Entity
@Table(name = "accounts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "account_type")
public abstract class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(unique = true)
    private String studentId;

    @Column(nullable = false)
    private String fullName;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ACTIVE;

    @JsonIgnore
    @Embedded
    private OtpCode otp;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Account() {}

    protected Account(String email, String phone, String studentId, String fullName, String passwordHash) {
        this.email = email;
        this.phone = phone;
        this.studentId = studentId;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }

    /** Saves the edited full name and phone number; Student ID and email are read-only (UC-F01-06). */
    public void update(Map<String, String> data) {
        if (data.containsKey("fullName")) this.fullName = data.get("fullName").trim();
        if (data.containsKey("phone")) this.phone = data.get("phone").trim();
    }

    public void updatePassword(String passwordHash) { this.passwordHash = passwordHash; }

    /** Stores the verification code with its expiry time, replacing an older one (UC-F01-04). */
    public void saveOtp(String code, LocalDateTime expiry) { this.otp = new OtpCode(code, expiry); }

    public OtpCode getOtp() { return otp == null || otp.getCode() == null ? null : otp; }

    public void deleteOtp() { this.otp = null; }

    /** USER, OPERATOR or ADMIN depending on the subclass; used to redirect after sign-in. */
    public abstract Role getRole();

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStudentId() { return studentId; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

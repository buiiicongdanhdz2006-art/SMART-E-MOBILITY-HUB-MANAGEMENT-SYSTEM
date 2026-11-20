package vn.edu.hcmut.emobility.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/** 6-digit verification code for password reset, valid 5 minutes (UC-F01-04). */
@Embeddable
public class OtpCode {

    @Column(name = "otp_code")
    private String code;

    @Column(name = "otp_expiry")
    private LocalDateTime expiry;

    protected OtpCode() {}

    public OtpCode(String code, LocalDateTime expiry) {
        this.code = code;
        this.expiry = expiry;
    }

    /** True if now is after the expiry time of the code. */
    public boolean isExpired(LocalDateTime now) { return expiry == null || now.isAfter(expiry); }

    public String getCode() { return code; }
    public LocalDateTime getExpiry() { return expiry; }
}

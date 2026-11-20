package vn.edu.hcmut.emobility.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends e-mails (OTP for password reset, e-mail channel of alerts).
 * In the MVP the e-mail is printed to the console instead of using a real SMTP server.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public boolean sendOtp(String email, String otp) {
        log.info("[EMAIL] To: {} | Mã xác thực đặt lại mật khẩu: {} (hiệu lực 5 phút)", email, otp);
        return true;
    }

    public boolean send(String email, String subject, String body) {
        log.info("[EMAIL] To: {} | {} | {}", email, subject, body);
        return true;
    }
}

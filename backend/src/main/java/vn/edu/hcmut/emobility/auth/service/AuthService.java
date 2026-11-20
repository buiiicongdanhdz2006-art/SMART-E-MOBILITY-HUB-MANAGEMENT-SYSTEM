package vn.edu.hcmut.emobility.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.OtpCode;
import vn.edu.hcmut.emobility.auth.entity.Session;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.auth.repository.SessionRepository;
import vn.edu.hcmut.emobility.common.enums.AccountStatus;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.PasswordHasher;

/** Business logic of AuthController (F-01: UC-F01-01 .. UC-F01-04). */
@Service
public class AuthService {

    public static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    public static final Pattern PHONE = Pattern.compile("^0\\d{9}$");
    public static final Pattern STUDENT_ID = Pattern.compile("^\\d{7,8}$");
    /** Vietnamese plate number, e.g. 59X1-123.45, 51F-12345, 29A-123.45. */
    public static final Pattern PLATE = Pattern.compile("^\\d{2}[A-Z][A-Z0-9]?[- ]?\\d{3}\\.?\\d{2}$");
    private static final int OTP_MINUTES = 5;

    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final EmailService emailService;
    private final int sessionHours;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AccountRepository accounts, SessionRepository sessions, EmailService emailService,
                       @Value("${auth.session-hours:12}") int sessionHours) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.emailService = emailService;
        this.sessionHours = sessionHours;
    }

    public record LoginResult(String token, String role, Long accountId, String fullName, String email) {}

    /** UC-F01-01: validates, checks the Student ID / email is not registered, hashes the password and creates a User. */
    @Transactional
    public Account register(String email, String phone, String studentId, String name, String password) {
        List<String> errors = validate(Map.of(
                "email", nz(email), "phone", nz(phone), "studentId", nz(studentId), "fullName", nz(name), "password", nz(password)));
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        if (accounts.findByEmailIgnoreCase(email.trim()).isPresent() || accounts.findByStudentId(studentId.trim()).isPresent()) {
            throw ApiException.conflict("Email hoặc MSSV đã được đăng ký");
        }
        if (accounts.existsPhone(phone.trim(), -1L)) throw ApiException.conflict("Số điện thoại đã được sử dụng");
        User user = new User(email.trim().toLowerCase(), phone.trim(), studentId.trim(), name.trim(), hash(password));
        return accounts.save(user);
    }

    /** UC-F01-02: finds the account by email or Student ID, checks the password, creates a session. */
    @Transactional
    public LoginResult login(String id, String password) {
        Account account = accounts.find(id).orElse(null);
        if (account == null || !verifyPassword(password, account.getPasswordHash())) {
            throw ApiException.unauthorized("Sai tài khoản hoặc mật khẩu");
        }
        if (account.getStatus() == AccountStatus.LOCKED) throw ApiException.forbidden("Tài khoản đã bị khoá");
        Session session = sessions.save(Session.create(account.getId(), sessionHours));
        return new LoginResult(session.getToken(), account.getRole().name(), account.getId(), account.getFullName(), account.getEmail());
    }

    /** UC-F01-03: deletes the session on the server. */
    @Transactional
    public boolean logout(String sessionToken) {
        if (sessionToken == null || !sessions.existsById(sessionToken)) return false;
        sessions.deleteById(sessionToken);
        return true;
    }

    /** UC-F01-04: generates an OTP, saves it in the account and e-mails it. False if the e-mail does not exist. */
    @Transactional
    public boolean requestOtp(String email) {
        Account account = accounts.findByEmailIgnoreCase(nz(email).trim()).orElse(null);
        if (account == null) return false;
        String otp = generateOtp();
        account.saveOtp(otp, LocalDateTime.now().plusMinutes(OTP_MINUTES));
        return emailService.sendOtp(account.getEmail(), otp);
    }

    /** UC-F01-04: checks the OTP, saves the new hashed password and deletes the OTP. */
    @Transactional
    public boolean resetPassword(String email, String otp, String newPassword) {
        Account account = accounts.findByEmailIgnoreCase(nz(email).trim()).orElseThrow(() -> ApiException.notFound("tài khoản"));
        OtpCode saved = account.getOtp();
        if (saved == null || !saved.getCode().equals(nz(otp).trim())) throw ApiException.badRequest("Mã OTP không đúng");
        if (saved.isExpired(LocalDateTime.now())) throw ApiException.badRequest("Mã OTP đã hết hạn");
        if (nz(newPassword).length() < 6) throw ApiException.badRequest("Mật khẩu phải có ít nhất 6 ký tự");
        account.updatePassword(hash(newPassword));
        account.deleteOtp();
        return true;
    }

    /** Checks required fields and formats; returns the error messages, empty if valid. */
    public static List<String> validate(Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        if (data.containsKey("email") && !EMAIL.matcher(data.get("email").trim()).matches()) errors.add("Email không hợp lệ");
        if (data.containsKey("phone") && !PHONE.matcher(data.get("phone").trim()).matches()) errors.add("Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0");
        if (data.containsKey("studentId") && !STUDENT_ID.matcher(data.get("studentId").trim()).matches()) errors.add("MSSV phải gồm 7-8 chữ số");
        if (data.containsKey("fullName") && data.get("fullName").trim().length() < 2) errors.add("Họ tên là bắt buộc");
        if (data.containsKey("password") && data.get("password").length() < 6) errors.add("Mật khẩu phải có ít nhất 6 ký tự");
        if (data.containsKey("plate") && !PLATE.matcher(normalizePlate(data.get("plate"))).matches()) errors.add("Biển số không đúng định dạng (vd: 59X1-123.45)");
        return errors;
    }

    public static String normalizePlate(String plate) { return plate == null ? "" : plate.trim().toUpperCase(); }

    boolean verifyPassword(String password, String hash) { return PasswordHasher.verify(password, hash); }

    String hash(String password) { return PasswordHasher.hash(password); }

    /** Random 6-digit code. */
    String generateOtp() { return String.format("%06d", random.nextInt(1_000_000)); }

    private static String nz(String s) { return s == null ? "" : s; }
}

package vn.edu.hcmut.emobility.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.service.AuthService;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;

/** «control» AuthController - F-01 register / login / logout / reset password. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** UC-F01-01 */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        Account a = auth.register(body.get("email"), body.get("phone"), body.get("studentId"), body.get("name"), body.get("password"));
        return Map.of("id", a.getId(), "email", a.getEmail(), "message", "Đăng ký thành công");
    }

    /** UC-F01-02 */
    @PostMapping("/login")
    public AuthService.LoginResult login(@RequestBody Map<String, String> body) {
        return auth.login(body.get("id"), body.get("password"));
    }

    /** UC-F01-03 */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        CurrentAccount.require(request);
        return Map.of("success", auth.logout(CurrentAccount.token(request)));
    }

    /** UC-F01-04 step 1: send OTP. */
    @PostMapping("/otp")
    public Map<String, Object> requestOtp(@RequestBody Map<String, String> body) {
        if (!auth.requestOtp(body.get("email"))) throw ApiException.notFound("email trong hệ thống");
        return Map.of("success", true, "message", "Đã gửi mã OTP tới email (MVP: xem log của backend)");
    }

    /** UC-F01-04 step 2: reset password with the OTP. */
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> body) {
        return Map.of("success", auth.resetPassword(body.get("email"), body.get("otp"), body.get("newPassword")));
    }
}

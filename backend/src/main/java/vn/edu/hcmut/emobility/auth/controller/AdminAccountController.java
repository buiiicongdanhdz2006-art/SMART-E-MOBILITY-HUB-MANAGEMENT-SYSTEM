package vn.edu.hcmut.emobility.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.auth.service.AuthService;
import vn.edu.hcmut.emobility.common.enums.AccountStatus;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.common.security.PasswordHasher;
import vn.edu.hcmut.emobility.common.security.Permissions;

/** Admin: manages user accounts (lock / unlock) and creates Operator accounts. */
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AccountRepository accounts;

    public AdminAccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<Account> list(HttpServletRequest request) {
        CurrentAccount.require(request, Role.ADMIN);
        return accounts.findAll();
    }

    @PutMapping("/{id}/status")
    @Transactional
    public Account setStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        Account admin = CurrentAccount.require(request, Role.ADMIN);
        if (admin.getId().equals(id)) throw ApiException.badRequest("Không thể tự khoá tài khoản của mình");
        Account a = accounts.findById(id).orElseThrow(() -> ApiException.notFound("tài khoản"));
        a.setStatus(AccountStatus.valueOf(body.getOrDefault("status", "ACTIVE")));
        return accounts.save(a);
    }

    @PostMapping("/operators")
    @Transactional
    public Account createOperator(HttpServletRequest request, @RequestBody Map<String, String> body) {
        CurrentAccount.require(request, Role.ADMIN);
        List<String> errors = AuthService.validate(Map.of(
                "email", body.getOrDefault("email", ""), "phone", body.getOrDefault("phone", ""),
                "fullName", body.getOrDefault("fullName", ""), "password", body.getOrDefault("password", "")));
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        if (accounts.findByEmailIgnoreCase(body.get("email")).isPresent()) throw ApiException.conflict("Email đã tồn tại");
        Operator op = new Operator(body.get("email").trim().toLowerCase(), body.get("phone"), body.get("fullName"),
                PasswordHasher.hash(body.get("password")), body.getOrDefault("permissions", Permissions.ALL));
        return accounts.save(op);
    }
}

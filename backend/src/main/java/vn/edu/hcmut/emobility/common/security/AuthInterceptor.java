package vn.edu.hcmut.emobility.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Session;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.auth.repository.SessionRepository;
import vn.edu.hcmut.emobility.common.enums.AccountStatus;

/**
 * Session filter: reads "Authorization: Bearer &lt;token&gt;", loads the session and puts the
 * signed-in Account into the request attribute {@link CurrentAccount#ATTR}. Role checks are
 * done in the controllers with {@link CurrentAccount#require}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionRepository sessions;
    private final AccountRepository accounts;

    public AuthInterceptor(SessionRepository sessions, AccountRepository accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return true; // public or rejected later by require()
        String token = header.substring(7).trim();
        Session session = sessions.findById(token).orElse(null);
        if (session == null || session.isExpired(LocalDateTime.now())) return true;
        Account account = accounts.findById(session.getAccountId()).orElse(null);
        if (account != null && account.getStatus() == AccountStatus.ACTIVE) {
            request.setAttribute(CurrentAccount.ATTR, account);
            request.setAttribute(CurrentAccount.TOKEN_ATTR, token);
        }
        return true;
    }
}

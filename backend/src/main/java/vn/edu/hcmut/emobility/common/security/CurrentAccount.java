package vn.edu.hcmut.emobility.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.common.exception.ApiException;

/** Helper for the role check that runs on every protected API. */
public final class CurrentAccount {

    public static final String ATTR = "emobility.account";
    public static final String TOKEN_ATTR = "emobility.token";

    private CurrentAccount() {}

    /** Returns the signed-in account, or throws 401 / 403 if not signed in or role not allowed. */
    public static Account require(HttpServletRequest request, Role... roles) {
        Object a = request.getAttribute(ATTR);
        if (!(a instanceof Account account)) throw ApiException.unauthorized("Bạn cần đăng nhập");
        if (roles.length > 0 && Arrays.stream(roles).noneMatch(r -> r == account.getRole())) {
            throw ApiException.forbidden("Bạn không có quyền thực hiện chức năng này");
        }
        return account;
    }

    public static User requireUser(HttpServletRequest request) {
        return (User) require(request, Role.USER);
    }

    /** Operator with the given permission (Operator.hasPermission in the design). */
    public static Operator requireOperator(HttpServletRequest request, String permission) {
        Operator op = (Operator) require(request, Role.OPERATOR);
        if (permission != null && !op.hasPermission(permission)) {
            throw ApiException.forbidden("Operator không có quyền " + permission);
        }
        return op;
    }

    public static String token(HttpServletRequest request) {
        Object t = request.getAttribute(TOKEN_ATTR);
        return t == null ? null : t.toString();
    }
}

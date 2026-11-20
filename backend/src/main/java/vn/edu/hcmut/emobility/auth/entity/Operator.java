package vn.edu.hcmut.emobility.auth.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.Arrays;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.common.enums.Severity;
import vn.edu.hcmut.emobility.common.security.Permissions;

@Entity
@DiscriminatorValue("OPERATOR")
public class Operator extends Account {

    /** Comma separated permissions, e.g. "MANAGE_CHARGING,DISPATCH" or "ALL". */
    private String permissions = Permissions.ALL;

    protected Operator() {}

    public Operator(String email, String phone, String fullName, String passwordHash, String permissions) {
        super(email, phone, null, fullName, passwordHash);
        this.permissions = permissions;
    }

    @Override
    public Role getRole() { return Role.OPERATOR; }

    /** True if the Operator holds permission p (F-07, F-08, F-09, F-10, F-11). */
    public boolean hasPermission(String p) {
        if (permissions == null) return false;
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .anyMatch(x -> x.equals(Permissions.ALL) || x.equals(p));
    }

    public boolean isSenior() {
        return permissions != null && Arrays.stream(permissions.split(",")).map(String::trim).anyMatch(Permissions.SENIOR::equals);
    }

    /** True if a case with this severity is within the Operator's authority (F11-AF-02). */
    public boolean isWithinScope(Severity severity) {
        return isSenior() || severity == null || severity.ordinal() <= Severity.HIGH.ordinal();
    }

    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
}

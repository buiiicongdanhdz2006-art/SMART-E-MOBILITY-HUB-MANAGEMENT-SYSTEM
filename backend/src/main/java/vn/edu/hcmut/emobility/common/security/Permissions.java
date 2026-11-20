package vn.edu.hcmut.emobility.common.security;

/** Operator permissions checked by Operator.hasPermission(p). */
public final class Permissions {
    public static final String ALL = "ALL";
    public static final String MANAGE_CHARGING = "MANAGE_CHARGING";
    public static final String VIEW_FLEET = "VIEW_FLEET";
    public static final String DISPATCH = "DISPATCH";
    public static final String MANAGE_INCIDENT = "MANAGE_INCIDENT";
    public static final String RUN_SIMULATION = "RUN_SIMULATION";
    public static final String ISSUE_ALERT = "ISSUE_ALERT";
    public static final String MANAGE_COMPLAINT = "MANAGE_COMPLAINT";
    /** Senior operator: receives escalated cases (F11-AF-02). */
    public static final String SENIOR = "SENIOR";

    private Permissions() {}
}

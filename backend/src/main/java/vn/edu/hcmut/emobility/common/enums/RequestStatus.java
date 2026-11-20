package vn.edu.hcmut.emobility.common.enums;

/** Charging request state (F-06 state chart). */
public enum RequestStatus {
    CREATED, WAITING, ASSIGNED, CHARGING, COMPLETED, CANCELLED, EXPIRED
}

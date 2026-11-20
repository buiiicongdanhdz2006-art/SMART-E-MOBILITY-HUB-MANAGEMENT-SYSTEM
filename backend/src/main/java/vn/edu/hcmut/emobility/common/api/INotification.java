package vn.edu.hcmut.emobility.common.api;

import vn.edu.hcmut.emobility.common.enums.AlertType;

/** Interface provided by Notification &amp; Alert (F-11, F-13). */
public interface INotification {
    void notifyUser(Long userId, AlertType type, String title, String message);
    void notifyOperators(AlertType type, String title, String message);
    void notifyMaintenanceStaff(String title, String message);
}

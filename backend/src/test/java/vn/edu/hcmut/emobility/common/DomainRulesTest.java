package vn.edu.hcmut.emobility.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.service.AuthService;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.IncidentStatus;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.LoadLevel;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.enums.Severity;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.common.enums.VehicleStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.security.PasswordHasher;
import vn.edu.hcmut.emobility.hub.dto.Location;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.service.HubService;
import vn.edu.hcmut.emobility.incident.entity.Incident;
import vn.edu.hcmut.emobility.notification.entity.Alert;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;

/** Rules of F-01, F-02, F-03, F-09, F-11 that do not need the database. */
class DomainRulesTest {

    @Test
    void passwordIsHashedAndVerified() {
        String h = PasswordHasher.hash("User@123");
        assertFalse(h.contains("User@123"));
        assertTrue(PasswordHasher.verify("User@123", h));
        assertFalse(PasswordHasher.verify("wrong", h));
        assertFalse(h.equals(PasswordHasher.hash("User@123"))); // random salt
    }

    @Test
    void registrationValidation() {
        assertTrue(AuthService.validate(Map.of("email", "a@hcmut.edu.vn", "phone", "0912345678", "studentId", "2410001",
                "fullName", "An", "password", "123456")).isEmpty());
        assertEquals(5, AuthService.validate(Map.of("email", "abc", "phone", "123", "studentId", "x",
                "fullName", "", "password", "1")).size());
        assertTrue(AuthService.validate(Map.of("plate", "59X1-123.45")).isEmpty());
        assertTrue(AuthService.validate(Map.of("plate", "51F-12345")).isEmpty());
        assertFalse(AuthService.validate(Map.of("plate", "ABC")).isEmpty());
    }

    @Test
    void loadLevelThresholds() {
        assertEquals(LoadLevel.NORMAL, HubService.determineLoadLevel(0.79));
        assertEquals(LoadLevel.WARNING, HubService.determineLoadLevel(0.80));
        assertEquals(LoadLevel.OVERLOADED, HubService.determineLoadLevel(0.95));
    }

    @Test
    void distanceBetweenMetroAndDormA() {
        double km = HubService.distance(new Location(10.8660, 106.8017), new Location(10.8786, 106.8065));
        assertTrue(km > 1.3 && km < 1.6, "km=" + km);
    }

    @Test
    void vehicleStateChart() {
        Hub hub = new Hub("H01", "Hub", "a", 10.0, 106.0, 3);
        Vehicle v = new Vehicle("V001", VehicleType.E_BIKE, "m", hub, 50);
        v.updateStatus(VehicleStatus.RESERVED);
        v.updateStatus(VehicleStatus.IN_USE);
        assertThrows(IllegalStateException.class, () -> v.updateStatus(VehicleStatus.CHARGING));
        v.updateStatus(VehicleStatus.AVAILABLE);
        assertTrue(v.hasEnoughBattery(50));
        assertFalse(v.hasEnoughBattery(51));
    }

    @Test
    void incidentStateChartKeepsHistory() {
        Incident i = new Incident(IncidentType.VEHICLE_FAULT, SeverityLevel.HIGH, null, null, null, "Hỏng phanh", "mô tả", 9L, false);
        i.investigate(2L);
        i.assignToMaintenance(2L);
        assertThrows(IllegalStateException.class, () -> i.markInvalid(2L, "x"));
        i.resolve(2L, "Đã thay má phanh");
        assertEquals(IncidentStatus.RESOLVED, i.getStatus());
        assertEquals(4, i.getStatusHistory().size());
        assertFalse(i.isOpen());
    }

    @Test
    void alertDeliveryFailureAndRetry() {
        Alert a = new Alert("Mưa lớn", "Hạn chế di chuyển", AlertType.GENERAL, Severity.HIGH, NotificationChannel.SMS, null, "H01", 2L, 1L);
        a.markDelivering();
        a.markDeliveryFailed("SMS down");
        assertEquals(CaseStatus.DELIVERY_FAILED, a.getStatus());
        a.changeChannel(NotificationChannel.IN_APP);
        a.markDelivering();
        a.markDelivered();
        assertEquals(CaseStatus.IN_PROGRESS, a.getStatus());
        a.markResolved();
        assertThrows(IllegalStateException.class, () -> a.updateStatus(CaseStatus.OPEN));
        assertEquals(2, a.getDeliveryAttempts());
    }

    @Test
    void operatorScopeAndPermissions() {
        Operator normal = new Operator("o@x.vn", "0900000000", "Op", "h", "MANAGE_CHARGING");
        Operator senior = new Operator("s@x.vn", "0900000001", "Senior", "h", "ALL,SENIOR");
        assertTrue(normal.hasPermission("MANAGE_CHARGING"));
        assertFalse(normal.hasPermission("DISPATCH"));
        assertTrue(senior.hasPermission("DISPATCH"));
        assertFalse(normal.isWithinScope(Severity.CRITICAL));
        assertTrue(senior.isWithinScope(Severity.CRITICAL));
    }

    @Test
    void hubOverloadThreshold() {
        Hub hub = new Hub("H01", "Hub", "a", 10.0, 106.0, 3);
        hub.setOverloadThreshold(5);
        assertFalse(hub.isOverloaded(5));
        assertTrue(hub.isOverloaded(6));
        assertTrue(LocalDateTime.now().isAfter(hub.getLastUpdated().minusSeconds(1)));
    }
}

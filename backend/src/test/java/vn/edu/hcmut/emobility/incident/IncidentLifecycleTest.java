package vn.edu.hcmut.emobility.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmut.emobility.common.enums.IncidentStatus;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.incident.dto.IncidentFilter;
import vn.edu.hcmut.emobility.incident.dto.IncidentReportRequest;
import vn.edu.hcmut.emobility.incident.entity.Incident;
import vn.edu.hcmut.emobility.incident.entity.IncidentStatusLog;

/** F-09 Incident & Maintenance: report validation, state chart and incident history filter. */
class IncidentLifecycleTest {

    private static Incident newIncident(String title) {
        return new Incident(IncidentType.VEHICLE_FAULT, SeverityLevel.HIGH, null, null, null, title, "Xe V001 phanh yếu", 9L, false);
    }

    // UC-F09-01 -----------------------------------------------------------------------------

    @Test
    void reportWithMissingFieldsIsRejected() {
        List<String> errors = new IncidentReportRequest(null, null, null, null, null, " ", null).validate();
        assertEquals(4, errors.size()); // type, title, description, related resource
    }

    @Test
    void completeReportIsAccepted() {
        assertTrue(new IncidentReportRequest(IncidentType.PORT_FAULT, SeverityLevel.MEDIUM, null, null, 5L, "Cổng không nhận sạc", "H01-P02")
                .validate().isEmpty());
    }

    @Test
    void newIncidentStartsReportedWithDefaultSeverity() {
        Incident i = new Incident(IncidentType.OTHER, null, null, null, null, "t", "d", 9L, false);
        assertEquals(IncidentStatus.REPORTED, i.getStatus());
        assertEquals(SeverityLevel.MEDIUM, i.getSeverity());
        assertEquals(1, i.getStatusHistory().size());
        assertTrue(i.isOpen());
    }

    // UC-F09-02 .. 04: state chart ------------------------------------------------------------

    @Test
    void mainPathReportedInvestigatingMaintenanceResolved() {
        Incident i = newIncident("Hỏng phanh");
        i.investigate(2L);
        assertEquals(IncidentStatus.INVESTIGATING, i.getStatus());
        i.assignToMaintenance(2L);
        assertEquals(IncidentStatus.MAINTENANCE, i.getStatus());
        i.resolve(2L, "Đã thay má phanh");
        assertEquals(IncidentStatus.RESOLVED, i.getStatus());
        assertFalse(i.isOpen());

        List<IncidentStatusLog> h = i.getStatusHistory();
        assertEquals(4, h.size());
        assertEquals(IncidentStatus.MAINTENANCE, h.get(3).getFromStatus());
        assertEquals(IncidentStatus.RESOLVED, h.get(3).getToStatus());
        assertEquals(Long.valueOf(2L), h.get(3).getOperatorId());
        assertEquals("Đã thay má phanh", h.get(3).getNote());
    }

    @Test
    void reportedIncidentCanGoStraightToMaintenance() {
        Incident i = newIncident("Tai nạn");
        i.assignToMaintenance(3L);
        assertEquals(IncidentStatus.MAINTENANCE, i.getStatus());
    }

    @Test
    void invalidTransitionsAreRejectedAndStateIsUnchanged() {
        Incident i = newIncident("x");
        assertThrows(IllegalStateException.class, () -> i.resolve(2L, "chưa kiểm tra"));
        assertEquals(IncidentStatus.REPORTED, i.getStatus());
        i.investigate(2L);
        assertThrows(IllegalStateException.class, () -> i.investigate(2L));
        i.assignToMaintenance(2L);
        i.resolve(2L, null);
        assertThrows(IllegalStateException.class, () -> i.assignToMaintenance(2L));
        assertThrows(IllegalStateException.class, () -> i.resolve(2L, "lần 2"));
        assertEquals(4, i.getStatusHistory().size());
    }

    @Test
    void falseAlarmNeedsReasonAndClosesIncident() {
        Incident i = newIncident("Cảm biến báo sai");
        assertThrows(IllegalArgumentException.class, () -> i.markInvalid(2L, "  "));
        i.investigate(2L);
        i.markInvalid(2L, "Cảm biến lỗi, xe bình thường");
        assertEquals(IncidentStatus.INVALID_CLOSED, i.getStatus());
        assertEquals("Cảm biến lỗi, xe bình thường", i.getInvalidReason());
        assertFalse(i.isOpen());
        assertThrows(IllegalStateException.class, () -> i.assignToMaintenance(2L));
    }

    // UC-F09-05: history filter -------------------------------------------------------------

    @Test
    void historyFilterByStatusKeywordAndDate() {
        Incident open = newIncident("Hỏng phanh xe V001");
        Incident done = new Incident(IncidentType.VEHICLE_FAULT, SeverityLevel.LOW, null, null, null, "Thủng lốp", "Lốp sau xe V002", 9L, false);
        done.investigate(2L);
        done.resolve(2L, "Đã vá");
        LocalDate today = LocalDate.now();

        assertTrue(new IncidentFilter(null, null, null, null, null, null).matches(open));
        assertTrue(new IncidentFilter(IncidentStatus.RESOLVED, null, null, null, null, null).matches(done));
        assertFalse(new IncidentFilter(IncidentStatus.RESOLVED, null, null, null, null, null).matches(open));
        assertTrue(new IncidentFilter(null, null, null, null, null, "PHANH").matches(open));
        assertFalse(new IncidentFilter(null, null, null, null, null, "phanh").matches(done));
        assertTrue(new IncidentFilter(null, today, today, null, null, null).matches(open));
        assertFalse(new IncidentFilter(null, today.plusDays(1), null, null, null, null).matches(open));
        assertFalse(new IncidentFilter(null, null, today.minusDays(1), null, null, null).matches(open));
        assertFalse(new IncidentFilter(null, null, null, 99L, null, null).matches(open));
    }
}

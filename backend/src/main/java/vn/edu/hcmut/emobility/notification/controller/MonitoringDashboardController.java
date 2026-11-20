package vn.edu.hcmut.emobility.notification.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.common.security.Permissions;
import vn.edu.hcmut.emobility.notification.entity.AlertFailureLog;
import vn.edu.hcmut.emobility.notification.entity.Case;
import vn.edu.hcmut.emobility.notification.entity.Complaint;
import vn.edu.hcmut.emobility.notification.service.AlertService;
import vn.edu.hcmut.emobility.notification.service.CaseDatabase;
import vn.edu.hcmut.emobility.notification.service.NotificationService;
import vn.edu.hcmut.emobility.simulation.entity.HubState;
import vn.edu.hcmut.emobility.simulation.service.HubDatabase;

/** REST side of the «boundary» MonitoringDashboard (F-11, Operator). */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringDashboardController {

    private final HubDatabase hubDatabase;
    private final CaseDatabase caseDb;
    private final AlertService alertService;
    private final NotificationService notificationService;

    public MonitoringDashboardController(HubDatabase hubDatabase, CaseDatabase caseDb, AlertService alertService,
                                         NotificationService notificationService) {
        this.hubDatabase = hubDatabase;
        this.caseDb = caseDb;
        this.alertService = alertService;
        this.notificationService = notificationService;
    }

    /** Vehicle, booking, charging and hub data for the Operator to review (F11-NF-01). */
    @GetMapping("/hubs/{hubId}")
    public HubState reviewOperationalData(HttpServletRequest request, @PathVariable Long hubId) {
        CurrentAccount.requireOperator(request, null);
        return hubDatabase.getData(hubId);
    }

    @GetMapping("/cases")
    public List<Case> cases(HttpServletRequest request, @RequestParam(required = false) CaseStatus status) {
        CurrentAccount.requireOperator(request, null);
        return caseDb.findAll().stream().filter(c -> status == null || c.getStatus() == status).toList();
    }

    @GetMapping("/cases/{caseId}/failures")
    public List<AlertFailureLog> failures(HttpServletRequest request, @PathVariable Long caseId) {
        CurrentAccount.requireOperator(request, null);
        return caseDb.failuresOf(caseId);
    }

    /** Passes the alert details (recipient / resource, type, severity, channel) to AlertService (F11-NF-03). */
    @PostMapping("/alerts")
    public Case issueAlert(HttpServletRequest request, @RequestBody Map<String, Object> details) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.ISSUE_ALERT);
        return alertService.createAlert(details, op);
    }

    /** Resend a failed alert, possibly on another channel (F11-AF-01). */
    @PostMapping("/alerts/{alertId}/retry")
    public Map<String, Object> retryDelivery(HttpServletRequest request, @PathVariable Long alertId, @RequestBody(required = false) Map<String, String> body) {
        CurrentAccount.requireOperator(request, Permissions.ISSUE_ALERT);
        NotificationChannel ch = null;
        if (body != null && body.get("channel") != null) {
            try { ch = NotificationChannel.valueOf(body.get("channel")); } catch (IllegalArgumentException e) { throw ApiException.badRequest("Kênh không hợp lệ"); }
        }
        boolean ok = alertService.retryDelivery(alertId, ch);
        return Map.of("delivered", ok, "case", caseDb.find(alertId));
    }

    /** Records a corrective action (notification or maintenance dispatch) (F11-NF-04). */
    @PostMapping("/cases/{caseId}/actions")
    @Transactional
    public Case takeCorrectiveAction(HttpServletRequest request, @PathVariable Long caseId, @RequestBody Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_COMPLAINT);
        Case c = caseDb.find(caseId);
        String note = body.get("note");
        if (note == null || note.isBlank()) throw ApiException.badRequest("Nhập nội dung xử lý");
        if (!op.isWithinScope(c.getSeverity())) throw ApiException.forbidden("Trường hợp vượt thẩm quyền, hãy chuyển tiếp (escalate)");
        if (c.getStatus() == CaseStatus.OPEN) c.updateStatus(CaseStatus.IN_PROGRESS);
        c.addActionNote(op.getFullName() + ": " + note.trim());
        return caseDb.updateCase(c);
    }

    /** Escalates a case beyond the Operator's authority (F11-AF-02). */
    @PostMapping("/cases/{caseId}/escalate")
    public Case escalateCase(HttpServletRequest request, @PathVariable Long caseId) {
        Operator op = CurrentAccount.requireOperator(request, null);
        return alertService.escalate(caseId, op);
    }

    /** RESOLVED + resolution notification to the User (F11-NF-05). */
    @PostMapping("/cases/{caseId}/resolve")
    @Transactional
    public Case confirmResolution(HttpServletRequest request, @PathVariable Long caseId, @RequestBody(required = false) Map<String, String> body) {
        CurrentAccount.requireOperator(request, Permissions.MANAGE_COMPLAINT);
        Case c = caseDb.find(caseId);
        if (c instanceof Complaint complaint && body != null && body.get("summary") != null) complaint.setResolution(body.get("summary"));
        caseDb.setStatus(caseId, CaseStatus.RESOLVED);
        alertService.notifyResolution(c);
        return caseDb.updateCase(c);
    }

    @PostMapping("/complaints/{caseId}/investigate")
    @Transactional
    public Case investigate(HttpServletRequest request, @PathVariable Long caseId) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_COMPLAINT);
        Case c = caseDb.find(caseId);
        if (!(c instanceof Complaint complaint)) throw ApiException.badRequest("Không phải khiếu nại");
        complaint.investigate();
        complaint.changeOwner(op.getId());
        complaint.addActionNote("Bắt đầu xử lý bởi " + op.getFullName());
        if (complaint.getRelatedUserId() != null) {
            notificationService.sendUpdate(complaint.getRelatedUserId(), "Khiếu nại #" + caseId + " đang được xử lý.");
        }
        return caseDb.updateCase(complaint);
    }

    /** Channels available now (SMS is disabled by default to demo F11-AF-01). */
    @GetMapping("/channels")
    public Map<String, Boolean> channels(HttpServletRequest request) {
        CurrentAccount.requireOperator(request, null);
        Map<String, Boolean> m = new java.util.LinkedHashMap<>();
        for (NotificationChannel c : NotificationChannel.values()) m.put(c.name(), notificationService.isChannelAvailable(c));
        return m;
    }

    /** Used by the dashboard to show "new complaint" notifications. */
    static void notifyNewComplaint(NotificationService n, Complaint c) {
        n.notifyOperators(AlertType.GENERAL, "Khiếu nại mới #" + c.getId(), c.getTitle());
    }
}

package vn.edu.hcmut.emobility.notification.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.common.enums.Severity;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.notification.entity.Complaint;
import vn.edu.hcmut.emobility.notification.service.CaseDatabase;

/** User.reportIssue(): a student reports a problem in-app; MonitoringDashboard.receiveComplaint stores it. */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final CaseDatabase caseDb;
    private final MonitoringDashboardController dashboard;

    public ComplaintController(CaseDatabase caseDb, MonitoringDashboardController dashboard) {
        this.caseDb = caseDb;
        this.dashboard = dashboard;
    }

    @PostMapping
    @Transactional
    public Complaint reportIssue(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = CurrentAccount.requireUser(request);
        String title = body.get("title");
        String description = body.get("description");
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw ApiException.badRequest("Nhập tiêu đề và mô tả vấn đề");
        }
        Severity severity = Severity.MEDIUM;
        try {
            if (body.get("severity") != null) severity = Severity.valueOf(body.get("severity"));
        } catch (IllegalArgumentException ignored) {
            // keep MEDIUM
        }
        Long hubId = body.get("hubId") == null || body.get("hubId").isBlank() ? null : Long.valueOf(body.get("hubId"));
        return dashboard.receiveComplaint(new Complaint(title.trim(), description.trim(), severity, u.getId(), hubId,
                body.getOrDefault("channel", "IN_APP")));
    }

    @GetMapping("/my")
    public List<Complaint> myComplaints(HttpServletRequest request) {
        return caseDb.findComplaintsByUser(CurrentAccount.requireUser(request).getId());
    }
}

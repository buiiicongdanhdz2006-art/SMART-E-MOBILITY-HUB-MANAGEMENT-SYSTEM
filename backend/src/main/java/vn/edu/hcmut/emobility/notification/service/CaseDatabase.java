package vn.edu.hcmut.emobility.notification.service;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.notification.entity.Alert;
import vn.edu.hcmut.emobility.notification.entity.AlertFailureLog;
import vn.edu.hcmut.emobility.notification.entity.Case;
import vn.edu.hcmut.emobility.notification.entity.Complaint;
import vn.edu.hcmut.emobility.notification.repository.AlertFailureLogRepository;
import vn.edu.hcmut.emobility.notification.repository.CaseRepository;

/** "CaseDatabase" of the F-11 class diagram: stores alerts, complaints and cases. */
@Component
public class CaseDatabase {

    private final CaseRepository cases;
    private final AlertFailureLogRepository failures;

    public CaseDatabase(CaseRepository cases, AlertFailureLogRepository failures) {
        this.cases = cases;
        this.failures = failures;
    }

    @Transactional
    public Complaint logComplaint(Complaint c) { return cases.save(c); }

    @Transactional
    public Alert saveAlert(Alert a) { return cases.save(a); }

    @Transactional
    public void logFailure(Long alertId, NotificationChannel channel, String reason) {
        failures.save(new AlertFailureLog(alertId, channel, reason));
    }

    @Transactional
    public Case updateCase(Case c) { return cases.save(c); }

    @Transactional
    public void updateOwner(Long caseId, Long newOwnerId) {
        Case c = find(caseId);
        c.changeOwner(newOwnerId);
    }

    @Transactional
    public void setStatus(Long caseId, CaseStatus status) {
        Case c = find(caseId);
        if (status == CaseStatus.RESOLVED) c.markResolved(); else c.updateStatus(status);
    }

    public Case find(Long caseId) {
        return cases.findById(caseId).orElseThrow(() -> ApiException.notFound("trường hợp #" + caseId));
    }

    public List<Case> findAll() { return cases.findAllByOrderByCreatedAtDesc(); }

    public List<Complaint> findComplaintsByUser(Long userId) { return cases.findComplaintsByUser(userId); }

    public List<AlertFailureLog> failuresOf(Long alertId) { return failures.findByAlertIdOrderByAtDesc(alertId); }
}

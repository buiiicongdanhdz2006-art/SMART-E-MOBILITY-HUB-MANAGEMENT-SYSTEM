package vn.edu.hcmut.emobility.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.Severity;

/** Complaint reported by a User through a support call or in-app (F-11). */
@Entity
@DiscriminatorValue("COMPLAINT")
public class Complaint extends Case {

    /** "IN_APP" or "CALL". */
    private String reportChannel;

    @Column(length = 2000)
    private String resolutionSummary;

    protected Complaint() {}

    public Complaint(String title, String description, Severity severity, Long userId, Long hubId, String reportChannel) {
        super(title, description, severity, null, userId, hubId);
        this.reportChannel = reportChannel == null ? "IN_APP" : reportChannel;
    }

    /** Begins the investigation: status IN_PROGRESS. */
    public void investigate() { updateStatus(CaseStatus.IN_PROGRESS); }

    public void setResolution(String summary) { this.resolutionSummary = summary; }

    @Override
    public String getCaseType() { return "COMPLAINT"; }

    public String getReportChannel() { return reportChannel; }
    public String getResolutionSummary() { return resolutionSummary; }
}

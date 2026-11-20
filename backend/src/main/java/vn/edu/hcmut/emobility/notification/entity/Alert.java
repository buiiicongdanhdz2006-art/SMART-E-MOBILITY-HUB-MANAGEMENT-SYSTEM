package vn.edu.hcmut.emobility.notification.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.enums.Severity;

/** Alert issued by an Operator about a potential risk (F-11). */
@Entity
@DiscriminatorValue("ALERT")
public class Alert extends Case {

    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    /** Target user; null = every user. */
    private Long recipientUserId;

    /** Resource concerned (hub, port, vehicle code...). */
    private String resource;

    private String failureReason;
    private int deliveryAttempts;

    protected Alert() {}

    public Alert(String title, String description, AlertType alertType, Severity severity, NotificationChannel channel,
                 Long recipientUserId, String resource, Long createdBy, Long hubId) {
        super(title, description, severity, createdBy, recipientUserId, hubId);
        this.alertType = alertType == null ? AlertType.GENERAL : alertType;
        this.channel = channel == null ? NotificationChannel.IN_APP : channel;
        this.recipientUserId = recipientUserId;
        this.resource = resource;
    }

    public void markDelivering() {
        updateStatus(CaseStatus.DELIVERING);
        deliveryAttempts++;
    }

    /** Successful delivery: the case becomes IN_PROGRESS. */
    public void markDelivered() {
        updateStatus(CaseStatus.IN_PROGRESS);
        this.failureReason = null;
    }

    public void markDeliveryFailed(String reason) {
        updateStatus(CaseStatus.DELIVERY_FAILED);
        this.failureReason = reason;
    }

    /** Changes the delivery channel before retrying (F11-AF-01). */
    public void changeChannel(NotificationChannel channel) { this.channel = channel; }

    @Override
    public String getCaseType() { return "ALERT"; }

    public AlertType getAlertType() { return alertType; }
    public NotificationChannel getChannel() { return channel; }
    public Long getRecipientUserId() { return recipientUserId; }
    public String getResource() { return resource; }
    public String getFailureReason() { return failureReason; }
    public int getDeliveryAttempts() { return deliveryAttempts; }
}

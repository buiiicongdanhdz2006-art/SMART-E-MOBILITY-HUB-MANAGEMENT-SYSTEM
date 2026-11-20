package vn.edu.hcmut.emobility.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;

/** One failed delivery attempt of an alert (F11-AF-01). */
@Entity
@Table(name = "alert_failure_logs")
public class AlertFailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long alertId;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    private String reason;
    private LocalDateTime at = LocalDateTime.now();

    protected AlertFailureLog() {}

    public AlertFailureLog(Long alertId, NotificationChannel channel, String reason) {
        this.alertId = alertId;
        this.channel = channel;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getAlertId() { return alertId; }
    public NotificationChannel getChannel() { return channel; }
    public String getReason() { return reason; }
    public LocalDateTime getAt() { return at; }
}

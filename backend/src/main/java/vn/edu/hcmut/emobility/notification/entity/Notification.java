package vn.edu.hcmut.emobility.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.enums.Role;

/**
 * In-app notification. recipientId = one account; recipientId null + recipientRole = broadcast to
 * every account of that role (e.g. all Operators).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long recipientId;

    @Enumerated(EnumType.STRING)
    private Role recipientRole;

    @Enumerated(EnumType.STRING)
    private AlertType type;

    private String title;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    private boolean readFlag;
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Notification() {}

    public Notification(Long recipientId, Role recipientRole, AlertType type, String title, String message, NotificationChannel channel) {
        this.recipientId = recipientId;
        this.recipientRole = recipientRole;
        this.type = type;
        this.title = title;
        this.message = message;
        this.channel = channel;
    }

    public void markRead() { this.readFlag = true; }

    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public Role getRecipientRole() { return recipientRole; }
    public AlertType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isRead() { return readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

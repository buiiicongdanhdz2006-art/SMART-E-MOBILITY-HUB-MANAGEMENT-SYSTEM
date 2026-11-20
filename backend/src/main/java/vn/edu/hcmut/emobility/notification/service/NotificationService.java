package vn.edu.hcmut.emobility.notification.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.auth.service.EmailService;
import vn.edu.hcmut.emobility.charging.entity.ChargingSchedule;
import vn.edu.hcmut.emobility.charging.entity.PortAssignment;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.websocket.LiveUpdatePublisher;
import vn.edu.hcmut.emobility.notification.entity.Alert;
import vn.edu.hcmut.emobility.notification.entity.Notification;
import vn.edu.hcmut.emobility.notification.repository.NotificationRepository;

/**
 * Notification service shared by F-07, F-09, F-11 and F-13 (one class in the code for the
 * NotificationService of the three class diagrams).
 */
@Service
public class NotificationService implements INotification {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final AccountRepository accounts;
    private final EmailService emailService;
    private final LiveUpdatePublisher live;
    private final Set<NotificationChannel> disabledChannels;

    public NotificationService(NotificationRepository notifications, AccountRepository accounts,
                               EmailService emailService, LiveUpdatePublisher live,
                               @Value("${notification.disabled-channels:SMS}") String disabled) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.emailService = emailService;
        this.live = live;
        this.disabledChannels = Arrays.stream(disabled.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(NotificationChannel::valueOf)
                .collect(Collectors.toSet());
    }

    // ---- INotification (F-06, F-07, F-09, F-13) --------------------------------------------

    /** Sends a notification to a User: port assigned, queue position, charging done, request cancelled/expired... */
    @Override
    @Transactional
    public void notifyUser(Long userId, AlertType type, String title, String message) {
        save(new Notification(userId, Role.USER, type, title, message, NotificationChannel.IN_APP));
    }

    /** Sends a warning to every Operator: broken port, overloaded Hub, sensor data lost... */
    @Override
    @Transactional
    public void notifyOperators(AlertType type, String title, String message) {
        save(new Notification(null, Role.OPERATOR, type, title, message, NotificationChannel.IN_APP));
    }

    /** Sends the incident information to the maintenance personnel (F-09). */
    @Override
    @Transactional
    public void notifyMaintenanceStaff(String title, String message) {
        save(new Notification(null, Role.OPERATOR, AlertType.MAINTENANCE, "[Bảo trì] " + title, message, NotificationChannel.IN_APP));
        emailService.send("maintenance@emobility.local", title, message);
    }

    /** Sends a message to one Operator. */
    @Transactional
    public void notifyOperator(Operator op, String message) {
        save(new Notification(op.getId(), Role.OPERATOR, AlertType.GENERAL, "Thông báo hệ thống", message, NotificationChannel.IN_APP));
    }

    /** Sends the new charging schedule to the related Users and the Operators (UC-F07-02, UC-F07-03). */
    @Transactional
    public void sendScheduleUpdate(ChargingSchedule schedule) {
        for (PortAssignment a : schedule.getAssignments()) {
            String msg = a.isImmediate()
                    ? "Yêu cầu sạc #" + a.getRequestId() + " được gán cổng " + a.getPortCode() + ". Vui lòng cắm sạc trong 15 phút."
                    : "Yêu cầu sạc #" + a.getRequestId() + " dự kiến sạc tại cổng " + a.getPortCode() + " lúc " + a.getPlannedStart().toLocalTime().withNano(0) + ".";
            notifyUser(a.getUserId(), AlertType.SCHEDULE_UPDATE, "Cập nhật lịch sạc", msg);
        }
        notifyOperators(AlertType.SCHEDULE_UPDATE, "Lịch sạc mới - " + schedule.getHubName(),
                "Đã tạo lịch sạc với " + schedule.getAssignments().size() + " phân bổ, " + schedule.getWaitingCount() + " yêu cầu đang chờ"
                        + (schedule.isOverloaded() ? " (TRẠM QUÁ TẢI)" : "") + ".");
    }

    // ---- F-11 ----------------------------------------------------------------------------

    /** Sends an alert through the channel; returns false if delivery fails. */
    @Transactional
    public boolean send(Alert alert, NotificationChannel channel) {
        if (!isChannelAvailable(channel)) {
            log.warn("Kênh {} không khả dụng, gửi cảnh báo #{} thất bại", channel, alert.getId());
            return false;
        }
        String title = "[" + alert.getSeverity() + "] " + alert.getTitle();
        if (channel == NotificationChannel.EMAIL) {
            if (alert.getRecipientUserId() != null) {
                Account a = accounts.findById(alert.getRecipientUserId()).orElse(null);
                if (a == null) return false;
                emailService.send(a.getEmail(), title, alert.getDescription());
            } else {
                emailService.send("all-users@emobility.local", title, alert.getDescription());
            }
            return true;
        }
        if (channel == NotificationChannel.SMS || channel == NotificationChannel.PUSH) {
            log.info("[{}] {} -> {}", channel, title, alert.getRecipientUserId() == null ? "ALL USERS" : alert.getRecipientUserId());
        }
        save(new Notification(alert.getRecipientUserId(), Role.USER, alert.getAlertType(), title, alert.getDescription(), channel));
        return true;
    }

    /** Sends a status update or resolution notification to the User. */
    @Transactional
    public boolean sendUpdate(Long userId, String message) {
        if (userId == null) return false;
        notifyUser(userId, AlertType.GENERAL, "Cập nhật xử lý", message);
        return true;
    }

    /** True if the notification channel is active (channels can be disabled in application.yml). */
    public boolean isChannelAvailable(NotificationChannel channel) {
        return !disabledChannels.contains(channel);
    }

    // ---- inbox ---------------------------------------------------------------------------

    public List<Notification> inbox(Account account) {
        return notifications.findForAccount(account.getId(), account.getRole());
    }

    @Transactional
    public void markRead(Long id, Account account) {
        Notification n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("thông báo"));
        boolean mine = account.getId().equals(n.getRecipientId())
                || (n.getRecipientId() == null && n.getRecipientRole() == account.getRole());
        if (!mine) throw ApiException.forbidden("Không phải thông báo của bạn");
        n.markRead();
    }

    private void save(Notification n) {
        notifications.save(n);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientId", n.getRecipientId());
        payload.put("recipientRole", n.getRecipientRole());
        payload.put("alertType", n.getType());
        payload.put("title", n.getTitle());
        payload.put("message", n.getMessage());
        live.publish("notification", payload);
    }
}

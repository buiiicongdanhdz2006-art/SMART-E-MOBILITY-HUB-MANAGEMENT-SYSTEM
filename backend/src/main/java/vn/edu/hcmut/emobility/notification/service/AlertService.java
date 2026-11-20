package vn.edu.hcmut.emobility.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.CaseStatus;
import vn.edu.hcmut.emobility.common.enums.NotificationChannel;
import vn.edu.hcmut.emobility.common.enums.Severity;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.notification.entity.Alert;
import vn.edu.hcmut.emobility.notification.entity.Case;

/** AlertService of F-11: create / dispatch / retry alerts, escalate and close cases. */
@Service
public class AlertService {

    private final CaseDatabase caseDb;
    private final NotificationService notificationService;
    private final AccountRepository accounts;

    public AlertService(CaseDatabase caseDb, NotificationService notificationService, AccountRepository accounts) {
        this.caseDb = caseDb;
        this.notificationService = notificationService;
        this.accounts = accounts;
    }

    /** Creates an Alert from the details entered by the Operator, saves it and sends it (F11-NF-03). */
    @Transactional
    public Alert createAlert(Map<String, Object> details, Operator operator) {
        List<String> errors = new ArrayList<>();
        String title = str(details.get("title"));
        String description = str(details.get("description"));
        if (title == null) errors.add("Tiêu đề là bắt buộc");
        if (description == null) errors.add("Nội dung là bắt buộc");
        AlertType type = parseEnum(AlertType.class, details.get("alertType"), AlertType.GENERAL, errors);
        Severity severity = parseEnum(Severity.class, details.get("severity"), Severity.MEDIUM, errors);
        NotificationChannel channel = parseEnum(NotificationChannel.class, details.get("channel"), NotificationChannel.IN_APP, errors);
        Long recipient = toLong(details.get("recipientUserId"));
        Long hubId = toLong(details.get("hubId"));
        if (!errors.isEmpty()) throw ApiException.validation(errors);

        Alert alert = new Alert(title, description, type, severity, channel, recipient, str(details.get("resource")),
                operator.getId(), hubId);
        caseDb.saveAlert(alert);
        dispatchAlert(alert, channel);
        return alert;
    }

    /** Sends the alert through NotificationService; if delivery fails, calls handleDeliveryFailure(). */
    @Transactional
    public boolean dispatchAlert(Alert alert, NotificationChannel channel) {
        alert.markDelivering();
        boolean ok = notificationService.send(alert, channel);
        if (ok) {
            alert.markDelivered();
            alert.addActionNote("Đã gửi qua kênh " + channel);
        } else {
            handleDeliveryFailure(alert, channel);
        }
        caseDb.updateCase(alert);
        return ok;
    }

    /** Records the error, sets DELIVERY_FAILED and notifies the Operator (F11-AF-01). */
    void handleDeliveryFailure(Alert alert, NotificationChannel channel) {
        String reason = "Kênh " + channel + " không khả dụng";
        caseDb.logFailure(alert.getId(), channel, reason);
        alert.markDeliveryFailed(reason);
        alert.addActionNote("Gửi thất bại: " + reason);
        accounts.findById(alert.getOwnerId()).ifPresent(a -> {
            if (a instanceof Operator op) {
                notificationService.notifyOperator(op, "Cảnh báo #" + alert.getId() + " gửi thất bại qua " + channel
                        + ". Hãy gửi lại hoặc chọn kênh khác.");
            }
        });
    }

    /** Resends the alert, possibly through an alternative channel (F11-AF-01). */
    @Transactional
    public boolean retryDelivery(Long alertId, NotificationChannel altChannel) {
        Case c = caseDb.find(alertId);
        if (!(c instanceof Alert alert)) throw ApiException.badRequest("Trường hợp #" + alertId + " không phải cảnh báo");
        if (alert.getStatus() != CaseStatus.DELIVERY_FAILED) throw ApiException.conflict("Chỉ gửi lại cảnh báo đã gửi thất bại");
        if (altChannel != null) alert.changeChannel(altChannel);
        return dispatchAlert(alert, alert.getChannel());
    }

    /** Passes the case to a senior operator, updates the owner and sets ESCALATED (F11-AF-02). */
    @Transactional
    public Case escalate(Long caseId, Operator current) {
        Case c = caseDb.find(caseId);
        Operator senior = accounts.findAllOperators().stream()
                .filter(Operator::isSenior)
                .filter(o -> !o.getId().equals(current.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("Không có Operator cấp cao để chuyển tiếp"));
        caseDb.updateOwner(caseId, senior.getId());
        c.updateStatus(CaseStatus.ESCALATED);
        c.addActionNote("Chuyển tiếp từ " + current.getFullName() + " cho " + senior.getFullName());
        notificationService.notifyOperator(senior, "Bạn nhận trường hợp #" + caseId + " (" + c.getTitle() + ") được chuyển tiếp.");
        return caseDb.updateCase(c);
    }

    /** Sends a resolution notification to the related User (F11-NF-05). */
    public void notifyResolution(Case c) {
        if (c.getRelatedUserId() != null) {
            notificationService.sendUpdate(c.getRelatedUserId(), "Trường hợp #" + c.getId() + " \"" + c.getTitle() + "\" đã được giải quyết.");
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long toLong(Object o) {
        String s = str(o);
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, Object value, E def, List<String> errors) {
        String s = str(value);
        if (s == null) return def;
        try {
            return Enum.valueOf(type, s.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Giá trị " + s + " không hợp lệ cho " + type.getSimpleName());
            return def;
        }
    }
}

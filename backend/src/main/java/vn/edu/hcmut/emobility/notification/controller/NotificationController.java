package vn.edu.hcmut.emobility.notification.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.notification.entity.Notification;
import vn.edu.hcmut.emobility.notification.service.NotificationService;

/** In-app inbox of the signed-in account (User.receiveNotification). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Notification> inbox(HttpServletRequest request) {
        return notificationService.inbox(CurrentAccount.require(request));
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(HttpServletRequest request, @PathVariable Long id) {
        Account a = CurrentAccount.require(request);
        notificationService.markRead(id, a);
        return Map.of("success", true);
    }
}

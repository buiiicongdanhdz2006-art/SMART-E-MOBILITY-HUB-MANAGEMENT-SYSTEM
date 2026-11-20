package vn.edu.hcmut.emobility.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.hub.dto.AlertDetail;
import vn.edu.hcmut.emobility.hub.entity.OverloadAlert;
import vn.edu.hcmut.emobility.hub.service.HubService;

/** «control» AlertController - overload alerts of the Hubs (UC-F02-04). */
@RestController
@RequestMapping("/api/alerts/overload")
public class AlertController {

    private final HubService hubService;

    public AlertController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping
    public List<OverloadAlert> getOpenAlerts(HttpServletRequest request) {
        CurrentAccount.requireOperator(request, null);
        return hubService.getOpenAlerts();
    }

    @GetMapping("/{alertId}")
    public AlertDetail getAlertDetail(HttpServletRequest request, @PathVariable Long alertId) {
        CurrentAccount.requireOperator(request, null);
        return hubService.getAlertDetail(alertId);
    }

    @PostMapping("/{alertId}/resolve")
    public OverloadAlert resolveAlert(HttpServletRequest request, @PathVariable Long alertId, @RequestBody(required = false) Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, null);
        return hubService.resolveAlert(alertId, op.getId(), body == null ? null : body.get("note"));
    }
}

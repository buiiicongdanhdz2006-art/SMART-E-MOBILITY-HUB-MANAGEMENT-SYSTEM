package vn.edu.hcmut.emobility.incident.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.common.enums.IncidentStatus;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.common.security.Permissions;
import vn.edu.hcmut.emobility.incident.dto.IncidentFilter;
import vn.edu.hcmut.emobility.incident.dto.IncidentReportRequest;
import vn.edu.hcmut.emobility.incident.entity.Incident;
import vn.edu.hcmut.emobility.incident.service.IncidentMaintenanceService;

/** REST boundary of F-09. Any signed-in account can report; only Operators handle incidents. */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentMaintenanceService service;

    public IncidentController(IncidentMaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    public Incident reportIncident(HttpServletRequest request, @RequestBody IncidentReportRequest body) {
        Account a = CurrentAccount.require(request);
        return service.reportIncident(body, a.getId());
    }

    @GetMapping
    public List<Incident> getIncidentHistory(HttpServletRequest request,
                                             @RequestParam(required = false) IncidentStatus status,
                                             @RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to,
                                             @RequestParam(required = false) Long vehicleId,
                                             @RequestParam(required = false) Long hubId,
                                             @RequestParam(required = false) String keyword) {
        CurrentAccount.requireOperator(request, Permissions.MANAGE_INCIDENT);
        return service.getIncidentHistory(new IncidentFilter(status, date(from), date(to), vehicleId, hubId, keyword));
    }

    @GetMapping("/{id}")
    public Incident getIncidentDetail(HttpServletRequest request, @PathVariable Long id) {
        CurrentAccount.requireOperator(request, Permissions.MANAGE_INCIDENT);
        return service.getIncidentDetail(id);
    }

    @PostMapping("/{id}/investigate")
    public Incident investigate(HttpServletRequest request, @PathVariable Long id) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_INCIDENT);
        return service.investigateIncident(id, op);
    }

    @PostMapping("/{id}/invalid")
    public Incident markInvalid(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_INCIDENT);
        return service.markIncidentInvalid(id, op, body.get("reason"));
    }

    @PostMapping("/{id}/maintenance")
    public Incident assignToMaintenance(HttpServletRequest request, @PathVariable Long id) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_INCIDENT);
        return service.assignToMaintenance(id, op);
    }

    @PostMapping("/{id}/resolve")
    public Incident resolve(HttpServletRequest request, @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Operator op = CurrentAccount.requireOperator(request, Permissions.MANAGE_INCIDENT);
        return service.resolveIncident(id, op, body == null ? null : body.get("note"));
    }

    private static LocalDate date(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s);
    }
}

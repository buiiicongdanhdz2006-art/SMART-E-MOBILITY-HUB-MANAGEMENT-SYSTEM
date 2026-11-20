package vn.edu.hcmut.emobility.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.WaitingQueueRepository;
import vn.edu.hcmut.emobility.common.enums.HubState;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.common.security.CurrentAccount;
import vn.edu.hcmut.emobility.hub.dto.HubSummary;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.hub.service.HubService;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.parking.repository.ParkingSlotRepository;

/** Admin: manages the list of Hubs (create with N slots and M ports, update information / state). */
@RestController
@RequestMapping("/api/admin/hubs")
public class AdminHubController {

    private final HubRepository hubs;
    private final ParkingSlotRepository slots;
    private final ChargingPortRepository ports;
    private final WaitingQueueRepository queues;
    private final HubService hubService;

    public AdminHubController(HubRepository hubs, ParkingSlotRepository slots, ChargingPortRepository ports,
                              WaitingQueueRepository queues, HubService hubService) {
        this.hubs = hubs;
        this.slots = slots;
        this.ports = ports;
        this.queues = queues;
        this.hubService = hubService;
    }

    @PostMapping
    @Transactional
    public HubSummary create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        CurrentAccount.require(request, Role.ADMIN);
        List<String> errors = new ArrayList<>();
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (code == null) errors.add("Mã trạm là bắt buộc");
        if (name == null) errors.add("Tên trạm là bắt buộc");
        if (body.get("latitude") == null || body.get("longitude") == null) errors.add("Toạ độ là bắt buộc");
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        if (hubs.findByCode(code).isPresent()) throw ApiException.conflict("Mã trạm đã tồn tại");
        Hub hub = hubs.save(new Hub(code, name, str(body.get("address")), num(body.get("latitude")), num(body.get("longitude")),
                (int) num(body.getOrDefault("expectedDemand", 5))));
        queues.save(new WaitingQueue(hub));
        int nSlots = (int) num(body.getOrDefault("slots", 10));
        int nPorts = (int) num(body.getOrDefault("ports", 4));
        for (int i = 1; i <= nSlots; i++) slots.save(new ParkingSlot(String.format("%s-S%02d", code, i), hub));
        for (int i = 1; i <= nPorts; i++) ports.save(new ChargingPort(String.format("%s-P%02d", code, i), hub, i <= (nPorts + 1) / 2 ? "A" : "B", 7.0));
        return hubService.summary(hubs.findById(hub.getId()).orElseThrow());
    }

    @PutMapping("/{hubId}")
    @Transactional
    public HubSummary update(HttpServletRequest request, @PathVariable Long hubId, @RequestBody Map<String, Object> body) {
        CurrentAccount.require(request, Role.ADMIN);
        Hub hub = hubService.getHub(hubId);
        if (body.containsKey("name")) hub.setName(str(body.get("name")));
        if (body.containsKey("address")) hub.setAddress(str(body.get("address")));
        if (body.containsKey("state")) hub.setState(HubState.valueOf(str(body.get("state"))));
        if (body.containsKey("expectedDemand")) hub.setExpectedDemand((int) num(body.get("expectedDemand")));
        if (body.containsKey("overloadThreshold")) hub.setOverloadThreshold((int) num(body.get("overloadThreshold")));
        if (body.containsKey("openHour")) hub.setOpenHour((int) num(body.get("openHour")));
        if (body.containsKey("closeHour")) hub.setCloseHour((int) num(body.get("closeHour")));
        hub.touch();
        return hubService.summary(hubs.save(hub));
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString().trim();
    }

    private static double num(Object o) {
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            throw ApiException.badRequest("Giá trị số không hợp lệ: " + o);
        }
    }
}

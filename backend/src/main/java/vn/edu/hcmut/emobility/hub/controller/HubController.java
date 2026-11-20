package vn.edu.hcmut.emobility.hub.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.emobility.common.enums.Need;
import vn.edu.hcmut.emobility.hub.dto.HubStatus;
import vn.edu.hcmut.emobility.hub.dto.HubSummary;
import vn.edu.hcmut.emobility.hub.dto.Location;
import vn.edu.hcmut.emobility.hub.service.HubService;

/** «control» HubController - UC-F02-01 .. 03 (public: students can look at the Hubs before signing in). */
@RestController
@RequestMapping("/api/hubs")
public class HubController {

    private final HubService hubService;

    public HubController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping
    public List<HubSummary> getHubs() { return hubService.getHubs(); }

    @GetMapping("/{hubId}/status")
    public HubStatus getHubStatus(@PathVariable Long hubId) { return hubService.getHubStatus(hubId); }

    @GetMapping("/nearest")
    public List<HubSummary> findNearestHubs(@RequestParam double lat, @RequestParam double lng,
                                            @RequestParam(required = false) Need need) {
        return hubService.findNearestHubs(new Location(lat, lng), need);
    }
}

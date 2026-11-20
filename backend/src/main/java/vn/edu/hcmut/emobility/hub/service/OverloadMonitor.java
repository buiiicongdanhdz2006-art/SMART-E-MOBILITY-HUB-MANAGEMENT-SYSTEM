package vn.edu.hcmut.emobility.hub.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.common.api.INotification;
import vn.edu.hcmut.emobility.common.enums.AlertStatus;
import vn.edu.hcmut.emobility.common.enums.AlertType;
import vn.edu.hcmut.emobility.common.enums.LoadLevel;
import vn.edu.hcmut.emobility.common.websocket.LiveUpdatePublisher;
import vn.edu.hcmut.emobility.hub.dto.HubStatus;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.entity.OverloadAlert;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.hub.repository.OverloadAlertRepository;

/** Identifies overloaded Hubs every 30 seconds and raises an OverloadAlert (UC-F02-03 overload detection). */
@Component
public class OverloadMonitor {

    private static final Logger log = LoggerFactory.getLogger(OverloadMonitor.class);

    private final HubRepository hubs;
    private final OverloadAlertRepository alerts;
    private final HubService hubService;
    private final INotification notification;
    private final LiveUpdatePublisher live;

    public OverloadMonitor(HubRepository hubs, OverloadAlertRepository alerts, HubService hubService,
                           INotification notification, LiveUpdatePublisher live) {
        this.hubs = hubs;
        this.alerts = alerts;
        this.hubService = hubService;
        this.notification = notification;
        this.live = live;
    }

    @Scheduled(fixedDelayString = "${hub.overload-check-ms:30000}", initialDelay = 20000)
    @Transactional
    public void check() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Hub hub : hubs.findAll()) {
            HubStatus st = hubService.status(hub);
            snapshot.add(Map.of("hubId", hub.getId(), "loadLevel", st.loadLevel().name(),
                    "utilisation", st.utilisation(), "freeSlots", st.freeSlots(), "freePorts", st.freePorts(),
                    "availableVehicles", hub.countAvailable(), "waiting", st.waitingRequests()));
            if (st.loadLevel() == LoadLevel.OVERLOADED && !alerts.existsByHub_IdAndStatus(hub.getId(), AlertStatus.OPEN)) {
                String msg = "Trạm " + hub.getName() + " quá tải: sử dụng " + Math.round(st.utilisation() * 100)
                        + "%, " + st.waitingRequests() + " yêu cầu sạc đang chờ.";
                alerts.save(new OverloadAlert(hub, st.loadLevel(), st.utilisation(), msg));
                notification.notifyOperators(AlertType.HUB_OVERLOAD, "Trạm quá tải", msg);
                log.warn(msg);
            }
        }
        live.publish("hub-status", Map.of("hubs", snapshot));
    }
}

package vn.edu.hcmut.emobility.common.api;

import java.time.LocalDateTime;
import java.util.List;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** Interface provided by Hub Management (F-02) to the feature modules (component diagram). */
public interface IHubQuery {
    Hub getHub(Long hubId);
    List<Hub> getAllHubs();
    /** True if the Hub has at least one parking slot free in [start, end]. */
    boolean hasFreeParkingSlot(Long hubId, LocalDateTime start, LocalDateTime end);
}

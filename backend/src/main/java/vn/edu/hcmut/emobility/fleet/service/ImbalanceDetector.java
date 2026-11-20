package vn.edu.hcmut.emobility.fleet.service;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.emobility.fleet.dto.HubImbalance;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** «logic» compares available vehicles with demand for each Hub (UC-F08-02). */
@Component
public class ImbalanceDetector {

    /** Balance range: |gap| &lt;= max(1, 20% of demand). */
    public static int tolerance(int demand) { return Math.max(1, (int) Math.round(demand * 0.2)); }

    /** Hubs with shortage or surplus, the most severe first. */
    public List<HubImbalance> detect(List<Hub> hubs) {
        return detectFrom(hubs.stream().filter(Hub::isActive).map(HubImbalance::of).toList());
    }

    public List<HubImbalance> detectFrom(List<HubImbalance> snapshots) {
        return snapshots.stream()
                .filter(i -> !isWithinBalance(i))
                .sorted((a, b) -> Integer.compare(Math.abs(b.gap()), Math.abs(a.gap())))
                .toList();
    }

    /** True if the Hub is inside the normal balance range. */
    public boolean isWithinBalance(HubImbalance i) { return Math.abs(i.gap()) <= tolerance(i.demand()); }

    public boolean isWithinBalance(Hub hub) { return isWithinBalance(HubImbalance.of(hub)); }
}

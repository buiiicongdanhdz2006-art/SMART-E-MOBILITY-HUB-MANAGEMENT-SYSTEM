package vn.edu.hcmut.emobility.fleet.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.emobility.fleet.dto.HubImbalance;
import vn.edu.hcmut.emobility.fleet.dto.RedistributionSuggestion;

/** «logic» pairs shortage Hubs with surplus Hubs (UC-F08-03). */
@Component
public class RedistributionRecommender {

    /** Vehicles always kept at the source Hub. */
    public static final int MIN_RESERVE = 2;

    /** Greedy pairing: the most severe shortage is served first by the Hub with the biggest surplus. */
    public List<RedistributionSuggestion> recommend(List<HubImbalance> imbalances) {
        List<HubImbalance> shortages = imbalances.stream().filter(HubImbalance::isShortage)
                .sorted(Comparator.comparingInt(HubImbalance::gap)).toList();
        List<HubImbalance> surpluses = imbalances.stream().filter(i -> !i.isShortage())
                .sorted(Comparator.comparingInt(HubImbalance::gap).reversed()).toList();
        Map<Long, Integer> given = new HashMap<>();
        List<RedistributionSuggestion> result = new ArrayList<>();
        for (HubImbalance s : shortages) {
            int need = -s.gap();
            for (HubImbalance src : surpluses) {
                if (need <= 0) break;
                int already = given.getOrDefault(src.hubId(), 0);
                HubImbalance remaining = HubImbalance.of(src.hubId(), src.hubName(), src.available() - already, src.demand());
                int qty = Math.min(need, calculateTransferQuantity(s, remaining));
                if (qty <= 0) continue;
                given.merge(src.hubId(), qty, Integer::sum);
                need -= qty;
                result.add(new RedistributionSuggestion(src.hubId(), src.hubName(), s.hubId(), s.hubName(), qty,
                        s.calculateSeverity(), "Trạm " + s.hubName() + " thiếu " + (-s.gap()) + " xe, trạm "
                        + src.hubName() + " dư " + src.gap() + " xe"));
            }
        }
        return result;
    }

    /** Number of vehicles to move, keeping a minimum reserve (and the demand) at the source Hub. */
    public int calculateTransferQuantity(HubImbalance shortage, HubImbalance surplus) {
        int spare = Math.min(surplus.gap(), surplus.available() - MIN_RESERVE);
        return Math.max(0, Math.min(-shortage.gap(), spare));
    }
}

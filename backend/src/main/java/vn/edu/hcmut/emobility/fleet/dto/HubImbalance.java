package vn.edu.hcmut.emobility.fleet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.edu.hcmut.emobility.common.enums.ImbalanceType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/** «DTO» a Hub with a shortage or surplus of available vehicles (UC-F08-02). gap = available - demand. */
public record HubImbalance(Long hubId, String hubName, int available, int demand, int gap) {

    public static HubImbalance of(Hub h) {
        return new HubImbalance(h.getId(), h.getName(), h.countAvailable(), h.getExpectedDemand(), h.getBalanceGap());
    }

    public static HubImbalance of(Long hubId, String hubName, int available, int demand) {
        return new HubImbalance(hubId, hubName, available, demand, available - demand);
    }

    /** LOW to CRITICAL from the size of the gap compared with demand. */
    public SeverityLevel calculateSeverity() {
        double ratio = Math.abs(gap) / (double) Math.max(1, demand);
        if (ratio >= 0.75) return SeverityLevel.CRITICAL;
        if (ratio >= 0.5) return SeverityLevel.HIGH;
        if (ratio >= 0.3) return SeverityLevel.MEDIUM;
        return SeverityLevel.LOW;
    }

    /** True if the Hub lacks vehicles, false if it has a surplus. */
    public boolean isShortage() { return gap < 0; }

    @JsonProperty("type")
    public ImbalanceType getType() { return isShortage() ? ImbalanceType.SHORTAGE : ImbalanceType.SURPLUS; }

    @JsonProperty("severity")
    public SeverityLevel getSeverity() { return calculateSeverity(); }
}

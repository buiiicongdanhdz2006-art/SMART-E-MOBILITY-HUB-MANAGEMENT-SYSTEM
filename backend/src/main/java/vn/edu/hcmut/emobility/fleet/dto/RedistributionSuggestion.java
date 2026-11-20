package vn.edu.hcmut.emobility.fleet.dto;

import vn.edu.hcmut.emobility.common.enums.SeverityLevel;

/** «DTO» transfer suggestion from a surplus Hub to a shortage Hub (UC-F08-03). */
public record RedistributionSuggestion(Long fromHubId, String fromHubName, Long toHubId, String toHubName,
                                       int quantity, SeverityLevel severity, String reason) {

    /** Converts the suggestion into a pre-filled dispatch request when the Operator applies it. */
    public DispatchRequest toDispatchRequest() {
        return new DispatchRequest(fromHubId, toHubId, quantity, null, null, "Áp dụng gợi ý: " + reason);
    }
}

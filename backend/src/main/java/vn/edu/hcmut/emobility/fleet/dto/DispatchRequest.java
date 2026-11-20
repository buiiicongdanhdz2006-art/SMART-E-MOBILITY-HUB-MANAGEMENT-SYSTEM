package vn.edu.hcmut.emobility.fleet.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** «DTO» dispatch form (UC-F08-04). vehicleIds is optional: the vehicles with the most battery are chosen. */
public record DispatchRequest(Long fromHubId, Long toHubId, Integer quantity, List<Long> vehicleIds,
                              LocalDateTime plannedArrival, String note) {

    /** quantity &gt; 0, different Hubs, valid time; returns the errors. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (fromHubId == null || toHubId == null) errors.add("Chọn trạm nguồn và trạm đích");
        else if (fromHubId.equals(toHubId)) errors.add("Trạm nguồn và trạm đích phải khác nhau");
        if (quantity == null || quantity <= 0) errors.add("Số lượng xe phải lớn hơn 0");
        if (vehicleIds != null && !vehicleIds.isEmpty() && quantity != null && vehicleIds.size() != quantity) {
            errors.add("Số xe đã chọn không khớp số lượng");
        }
        if (plannedArrival != null && plannedArrival.isBefore(LocalDateTime.now())) errors.add("Thời gian đến dự kiến đã qua");
        return errors;
    }
}

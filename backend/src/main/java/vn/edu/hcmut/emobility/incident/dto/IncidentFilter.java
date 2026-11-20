package vn.edu.hcmut.emobility.incident.dto;

import java.time.LocalDate;
import vn.edu.hcmut.emobility.common.enums.IncidentStatus;
import vn.edu.hcmut.emobility.incident.entity.Incident;

/** «DTO» filter of the incident history (UC-F09-05). Null fields are ignored. */
public record IncidentFilter(IncidentStatus status, LocalDate from, LocalDate to, Long vehicleId, Long hubId, String keyword) {

    /** True if the incident satisfies the selected status, date, vehicle, Hub and keyword. */
    public boolean matches(Incident i) {
        if (status != null && i.getStatus() != status) return false;
        if (from != null && i.getCreatedAt().toLocalDate().isBefore(from)) return false;
        if (to != null && i.getCreatedAt().toLocalDate().isAfter(to)) return false;
        if (vehicleId != null && !vehicleId.equals(i.getVehicleId())) return false;
        if (hubId != null && !hubId.equals(i.getHubId())) return false;
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            String text = (i.getTitle() + " " + i.getDescription() + " " + i.getVehicleCode() + " " + i.getPortCode()).toLowerCase();
            if (!text.contains(k)) return false;
        }
        return true;
    }
}

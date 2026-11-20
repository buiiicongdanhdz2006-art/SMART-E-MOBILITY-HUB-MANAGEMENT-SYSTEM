package vn.edu.hcmut.emobility.incident.dto;

import java.util.ArrayList;
import java.util.List;
import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;

/** «DTO» incident report form (UC-F09-01). */
public record IncidentReportRequest(IncidentType type, SeverityLevel severity, Long hubId, Long vehicleId, Long portId,
                                    String title, String description) {

    /** Missing mandatory fields; empty if the report is complete. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (type == null) errors.add("Loại sự cố là bắt buộc");
        if (title == null || title.isBlank()) errors.add("Tiêu đề là bắt buộc");
        if (description == null || description.isBlank()) errors.add("Mô tả là bắt buộc");
        if (hubId == null && vehicleId == null && portId == null) errors.add("Chọn trạm, xe hoặc cổng sạc liên quan");
        return errors;
    }
}

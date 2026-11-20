package vn.edu.hcmut.emobility.common.api;

import vn.edu.hcmut.emobility.common.enums.IncidentType;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;

/** Interface provided by Incident &amp; Maintenance (F-09) to report incidents from other modules. */
public interface IIncidentReport {
    /** Creates an incident with status REPORTED and returns its id. reporterId is null for auto-detected ones. */
    Long reportIncident(IncidentType type, SeverityLevel severity, Long hubId, Long vehicleId, Long portId,
                        String title, String description, Long reporterId);

    /** True if there is already an incident of this type for the vehicle/port that is not closed. */
    boolean hasOpenIncident(IncidentType type, Long vehicleId, Long portId);
}

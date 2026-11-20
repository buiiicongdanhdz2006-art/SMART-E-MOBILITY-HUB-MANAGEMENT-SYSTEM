package vn.edu.hcmut.emobility.hub.dto;

import vn.edu.hcmut.emobility.hub.entity.OverloadAlert;

/** An overload alert with the current status of its Hub (UC-F02-04). */
public record AlertDetail(OverloadAlert alert, HubStatus currentHubStatus) {}

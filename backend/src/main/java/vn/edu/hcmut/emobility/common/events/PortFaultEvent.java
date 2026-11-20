package vn.edu.hcmut.emobility.common.events;

/** Published when a charging port must go to FIXING (e.g. incident assigned to maintenance). */
public record PortFaultEvent(Long portId, String note) {}

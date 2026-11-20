package vn.edu.hcmut.emobility.common.events;

/** Published when a port incident is resolved, so the scheduler can use the port again. */
public record PortRepairedEvent(Long portId) {}

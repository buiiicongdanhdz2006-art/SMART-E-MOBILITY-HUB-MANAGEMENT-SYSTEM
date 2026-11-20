package vn.edu.hcmut.emobility.hub.dto;

/** Coordinates of the user (from LocationService on the client: GPS / browser geolocation). */
public record Location(double latitude, double longitude) {}

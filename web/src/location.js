/** LocationService of F-02 (UC-F02-03): current coordinates of the device through browser geolocation. */
export const CAMPUS_CENTER = { lat: 10.8700, lng: 106.8030 };

/** Returns { lat, lng }; falls back to the centre of the campus if the browser refuses or times out. */
export function getCurrentLocation(timeout = 4000) {
  return new Promise((resolve) => {
    if (!navigator.geolocation) { resolve(CAMPUS_CENTER); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => resolve(CAMPUS_CENTER),
      { timeout },
    );
  });
}

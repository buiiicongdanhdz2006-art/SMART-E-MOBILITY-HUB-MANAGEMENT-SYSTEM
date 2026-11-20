#!/usr/bin/env python3
"""
IoT simulator for the Smart E-Mobility Hub (F-12).

It discovers the devices of the backend (GET /api/iot/devices) and, every tick, publishes sensor data:

  emobility/ports/{portCode}/plugged         vehicle plugged into an ASSIGNED port
  emobility/ports/{portCode}/battery         SOC / voltage / current / temperature of a CHARGING port
  emobility/ports/{portCode}/status          FIXING / AVAILABLE (random hardware faults and repairs)
  emobility/ports/{portCode}/lost            the port stops sending data for a while
  emobility/vehicles/{vehicleCode}/telemetry battery, position, speed, tyre pressure, driving event
  emobility/slots/{slotCode}/status          occupancy of the parking slot

The values are generated from the sample datasets of IoTDataset.com (see data/README.md).

Transport:
  --transport mqtt   publish to the Mosquitto broker (default, as in the deployment diagram)
  --transport http   POST /api/iot/events on the backend (no broker needed)

Examples:
  python simulator.py --transport http --api http://localhost:8080
  python simulator.py --mqtt-host localhost --speed 10 --fault-rate 0.01
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import os
import random
import statistics
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Dict, List, Optional

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CHARGING_CSV = os.path.join(HERE, "data", "ev_battery_charging_sample.csv")
DEFAULT_TELEMETRY_CSV = os.path.join(HERE, "data", "connected_car_telemetry_sample.csv")

# Centre of the National University urban area (Thu Duc / Di An) used to re-project dataset coordinates.
VNU_CENTER = (10.8750, 106.8010)
KM_PER_DEG_LAT = 111.32


def log(msg: str) -> None:
    print(time.strftime("%H:%M:%S"), msg, flush=True)


# --------------------------------------------------------------------------------------------
# Dataset models
# --------------------------------------------------------------------------------------------

@dataclass
class ChargingProfile:
    """Charging behaviour learnt from the EV Battery Charging sample."""
    soc_step: float          # SOC % gained per dataset step
    volt_base: float         # voltage at soc_base
    volt_per_soc: float      # voltage gained per 1 % SOC
    current_base: float      # constant-current phase current (A)
    temp_base: float         # starting temperature (C)
    temp_step: float         # temperature rise per step at the start
    soc_base: float
    noise_v: float
    noise_i: float
    noise_t: float
    soh: float

    @staticmethod
    def from_csv(path: str) -> "ChargingProfile":
        with open(path, newline="", encoding="utf-8") as f:
            rows = [r for r in csv.DictReader(f) if str(r.get("Charging_Status", "1")).strip() in ("1", "1.0")]
        if len(rows) < 2:
            raise ValueError(f"{path}: need at least 2 charging rows")
        col = lambda name: [float(r[name]) for r in rows]
        soc, volt, cur, temp = col("SOC"), col("Voltage"), col("Current"), col("Battery_Temperature")
        d = lambda xs: [b - a for a, b in zip(xs, xs[1:])]
        d_soc, d_v, d_i, d_t = d(soc), d(volt), d(cur), d(temp)
        soc_step = max(0.05, statistics.mean(d_soc))
        volt_per_soc = statistics.mean(d_v) / soc_step
        resid = lambda ds: statistics.pstdev([x - statistics.mean(ds) for x in ds]) if len(ds) > 1 else 0.0
        return ChargingProfile(
            soc_step=soc_step,
            volt_base=volt[0],
            volt_per_soc=volt_per_soc,
            current_base=statistics.mean(cur),
            temp_base=temp[0],
            temp_step=max(0.1, statistics.mean(d_t)),
            soc_base=soc[0],
            noise_v=resid(d_v),
            noise_i=resid(d_i),
            noise_t=resid(d_t),
            soh=statistics.mean(col("SOH")) if "SOH" in rows[0] else 98.0,
        )

    def voltage(self, soc: float) -> float:
        """Linear trend of the dataset, flattening above 80 % SOC (constant-voltage phase), at most +10 %."""
        effective = soc if soc < 80 else 80 + (soc - 80) * 0.3
        v = self.volt_base + self.volt_per_soc * (effective - self.soc_base)
        return min(v, self.volt_base * 1.10) + random.gauss(0, self.noise_v)

    def current(self, soc: float) -> float:
        """Constant current up to 80 % SOC, then linear taper to 20 % of the base current (CC-CV)."""
        base = self.current_base if soc < 80 else self.current_base * (1 - 0.8 * (soc - 80) / 20)
        return max(0.5, base + random.gauss(0, self.noise_i))

    def next_temperature(self, temp: float) -> float:
        """Rises like the dataset at first, then levels off around temp_base + 12 C."""
        plateau = self.temp_base + 12
        rise = self.temp_step * max(0.0, (plateau - temp) / (plateau - self.temp_base))
        return temp + rise + random.gauss(0, self.noise_t)


@dataclass
class TelemetryRow:
    speed: float
    heading: float
    accel: float
    tire: float
    event: str


class TelemetryProfiles:
    """Driving profiles per dataset vehicle from the Connected Car sample."""

    def __init__(self, path: str):
        self.by_vehicle: Dict[str, List[TelemetryRow]] = {}
        with open(path, newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                row = TelemetryRow(float(r["Speed_kmh"] or 0), float(r["Heading_deg"] or 0),
                                   float(r["Acceleration_ms2"] or 0), float(r["Tire_Pressure_kPa"] or 230),
                                   (r.get("Driving_Event") or "Normal").strip())
                self.by_vehicle.setdefault(r["Vehicle_ID"].strip(), []).append(row)
        if not self.by_vehicle:
            raise ValueError(f"{path}: no telemetry rows")
        self.ids = sorted(self.by_vehicle)
        speeds = [x.speed for rows in self.by_vehicle.values() for x in rows if x.speed > 0]
        self.moving_speed = statistics.mean(speeds) if speeds else 3.0

    def profile_for(self, vehicle_code: str) -> List[TelemetryRow]:
        if vehicle_code in self.by_vehicle:
            return self.by_vehicle[vehicle_code]
        digits = "".join(ch for ch in vehicle_code if ch.isdigit())
        idx = (int(digits) - 1 if digits else abs(hash(vehicle_code))) % len(self.ids)
        return self.by_vehicle[self.ids[idx]]


# --------------------------------------------------------------------------------------------
# Transports
# --------------------------------------------------------------------------------------------

class HttpTransport:
    def __init__(self, api: str, key: str):
        self.api = api.rstrip("/")
        self.key = key

    def publish(self, topic: str, payload: dict) -> None:
        body = json.dumps({"topic": topic, "payload": payload}).encode()
        req = urllib.request.Request(self.api + "/api/iot/events", data=body, method="POST",
                                     headers={"Content-Type": "application/json", "X-IoT-Key": self.key})
        try:
            urllib.request.urlopen(req, timeout=5).read()
        except urllib.error.URLError as e:
            log(f"[http] publish {topic} failed: {e}")

    def close(self) -> None:
        pass


class MqttTransport:
    def __init__(self, host: str, port: int):
        try:
            import paho.mqtt.client as mqtt
        except ImportError:
            sys.exit("paho-mqtt is not installed: pip install -r requirements.txt (or use --transport http)")
        try:  # paho-mqtt 2.x
            self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="emobility-iot-simulator")
        except AttributeError:  # paho-mqtt 1.x
            self.client = mqtt.Client(client_id="emobility-iot-simulator")
        self.client.connect(host, port, keepalive=30)
        self.client.loop_start()
        log(f"[mqtt] connected to {host}:{port}")

    def publish(self, topic: str, payload: dict) -> None:
        self.client.publish(topic, json.dumps(payload), qos=1)

    def close(self) -> None:
        self.client.loop_stop()
        self.client.disconnect()


# --------------------------------------------------------------------------------------------
# Simulator
# --------------------------------------------------------------------------------------------

@dataclass
class PortState:
    assigned_ticks: int = 0
    soc: Optional[float] = None
    temp: Optional[float] = None
    silent_ticks: int = 0
    fault_ticks: int = 0      # > 0 while a fault injected by the simulator is being repaired
    overheating: bool = False


@dataclass
class VehicleState:
    lat: Optional[float] = None
    lng: Optional[float] = None
    battery: Optional[float] = None
    step: int = 0
    tire_fault: bool = False


@dataclass
class Simulator:
    args: argparse.Namespace
    transport: object
    charging: ChargingProfile
    telemetry: TelemetryProfiles
    ports: Dict[str, PortState] = field(default_factory=dict)
    vehicles: Dict[str, VehicleState] = field(default_factory=dict)
    slots: Dict[str, bool] = field(default_factory=dict)
    sent: int = 0

    # ---- discovery ------------------------------------------------------------------------
    def devices(self) -> Optional[dict]:
        req = urllib.request.Request(self.args.api.rstrip("/") + "/api/iot/devices", headers={"X-IoT-Key": self.args.key})
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                return json.loads(r.read().decode())
        except (urllib.error.URLError, ValueError) as e:
            log(f"cannot read devices from {self.args.api}: {e}")
            return None

    def publish(self, topic: str, payload: dict) -> None:
        self.transport.publish(topic, payload)
        self.sent += 1
        if self.args.verbose:
            log(f"-> {topic} {json.dumps(payload)}")

    # ---- one tick -------------------------------------------------------------------------
    def tick(self) -> None:
        d = self.devices()
        if d is None:
            return
        for p in d.get("ports", []):
            self.tick_port(p)
        for v in d.get("vehicles", []):
            self.tick_vehicle(v)
        for s in d.get("slots", []):
            self.tick_slot(s)

    def tick_port(self, p: dict) -> None:
        code, status = p["code"], p["status"]
        st = self.ports.setdefault(code, PortState())
        rnd = random.random()

        # Repair of a fault injected by the simulator.
        if st.fault_ticks > 0:
            st.fault_ticks -= 1
            if st.fault_ticks == 0 and status == "FIXING":
                self.publish(f"emobility/ports/{code}/status", {"status": "AVAILABLE"})
                log(f"port {code} repaired")
            return
        if status == "FIXING":
            st.soc = st.temp = None
            return

        # Random hardware fault.
        if rnd < self.args.fault_rate:
            st.fault_ticks = self.args.repair_ticks
            st.soc = st.temp = None
            self.publish(f"emobility/ports/{code}/status", {"status": "FIXING"})
            log(f"port {code} -> FIXING (simulated fault)")
            return

        req_status = p.get("requestStatus")
        if req_status == "ASSIGNED":
            st.assigned_ticks += 1
            if st.assigned_ticks >= self.args.plug_delay:
                self.publish(f"emobility/ports/{code}/plugged", {"vehicleId": p.get("vehicleRef")})
                log(f"vehicle {p.get('vehicleRef')} plugged into {code}")
                st.assigned_ticks = 0
                st.soc = float(p.get("battery") or 20)
                st.temp = self.charging.temp_base + random.uniform(-1, 1)
            return
        st.assigned_ticks = 0

        if req_status != "CHARGING":
            st.soc = st.temp = None
            return

        # Silent port (lost connection) for a while.
        if st.silent_ticks > 0:
            st.silent_ticks -= 1
            if st.silent_ticks == 0:
                log(f"port {code} sends data again")
            return
        if random.random() < self.args.lost_rate:
            st.silent_ticks = self.args.lost_ticks
            self.publish(f"emobility/ports/{code}/lost", {})
            log(f"port {code} connection lost for {self.args.lost_ticks} ticks")
            return

        if st.soc is None:  # simulator restarted while a session was running
            st.soc = float(p.get("battery") or 20)
            st.temp = self.charging.temp_base
        target = float(p.get("targetBattery") or 100)
        taper = 1.0 if st.soc < 80 else max(0.25, 1 - (st.soc - 80) / 25)
        st.soc = min(100.0, target, st.soc + self.charging.soc_step * self.args.speed * taper)
        st.temp = self.charging.next_temperature(st.temp)
        if random.random() < self.args.overheat_rate:
            st.temp = 56 + random.uniform(0, 6)
            log(f"port {code} overheating {st.temp:.1f} C (simulated)")
        self.publish(f"emobility/ports/{code}/battery", {
            "soc": round(st.soc, 1),
            "voltage": round(self.charging.voltage(st.soc), 1),
            "current": round(self.charging.current(st.soc), 2),
            "temperature": round(st.temp, 1),
            "soh": round(self.charging.soh, 1),
            "charging_status": 1,
        })

    def tick_vehicle(self, v: dict) -> None:
        code, status = v["code"], v["status"]
        st = self.vehicles.setdefault(code, VehicleState())
        if v.get("lat") is not None and (st.lat is None or status not in ("IN_USE", "IN_TRANSIT")):
            st.lat, st.lng = v["lat"], v["lng"]
        if st.battery is None or status not in ("IN_USE", "IN_TRANSIT"):
            st.battery = float(v.get("battery") or 50)
        if status == "CHARGING":
            return  # battery comes from the charging port
        rows = self.telemetry.profile_for(code)
        row = rows[st.step % len(rows)]
        st.step += 1

        speed = 0.0
        event = "Idle"
        heading = row.heading
        if status in ("IN_USE", "IN_TRANSIT") and st.lat is not None:
            speed = max(row.speed, self.telemetry.moving_speed) * self.args.drive_speed
            event = row.event if row.event not in ("Idle",) else "Normal"
            if status == "IN_TRANSIT" and v.get("destLat") is not None:
                heading = bearing(st.lat, st.lng, v["destLat"], v["destLng"])
                remaining = haversine(st.lat, st.lng, v["destLat"], v["destLng"])
                step_km = speed * self.args.tick / 3600
                if step_km >= remaining:
                    st.lat, st.lng = v["destLat"], v["destLng"]
                else:
                    st.lat, st.lng = move(st.lat, st.lng, heading, step_km)
            else:
                st.lat, st.lng = move(st.lat, st.lng, heading + random.uniform(-25, 25), speed * self.args.tick / 3600)
                st.lat, st.lng = keep_on_campus(st.lat, st.lng)
            st.battery = max(0.0, st.battery - 0.02 * speed * self.args.tick / 2)

        tire = row.tire + random.gauss(0, 1.0)
        if not st.tire_fault and random.random() < self.args.tire_fault_rate:
            st.tire_fault = True
            log(f"vehicle {code} low tyre pressure (simulated)")
        if st.tire_fault:
            tire = 150 + random.uniform(0, 15)

        payload = {"battery": round(st.battery, 1), "speed": round(speed, 1), "heading": round(heading % 360),
                   "acceleration": row.accel if speed else 0, "tirePressure": round(tire, 1), "event": event}
        if st.lat is not None:
            payload.update({"lat": round(st.lat, 6), "lng": round(st.lng, 6)})
        self.publish(f"emobility/vehicles/{code}/telemetry", payload)

    def tick_slot(self, s: dict) -> None:
        occupied = bool(s.get("checkedIn"))
        if self.slots.get(s["code"]) != occupied:
            self.slots[s["code"]] = occupied
            self.publish(f"emobility/slots/{s['code']}/status", {"occupied": occupied})


# --------------------------------------------------------------------------------------------
# Geometry helpers
# --------------------------------------------------------------------------------------------

def haversine(lat1, lng1, lat2, lng2) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing(lat1, lng1, lat2, lng2) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lng2 - lng1)
    x = math.sin(dl) * math.cos(p2)
    y = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def move(lat, lng, heading_deg, km):
    dlat = km * math.cos(math.radians(heading_deg)) / KM_PER_DEG_LAT
    dlng = km * math.sin(math.radians(heading_deg)) / (KM_PER_DEG_LAT * math.cos(math.radians(lat)))
    return lat + dlat, lng + dlng


def keep_on_campus(lat, lng, radius_km=2.5):
    """Vehicles in use wander inside the National University area."""
    if haversine(lat, lng, *VNU_CENTER) <= radius_km:
        return lat, lng
    return lat + (VNU_CENTER[0] - lat) * 0.2, lng + (VNU_CENTER[1] - lng) * 0.2


# --------------------------------------------------------------------------------------------

def parse_args(argv=None):
    p = argparse.ArgumentParser(description="IoT simulator for the Smart E-Mobility Hub")
    p.add_argument("--transport", choices=["mqtt", "http"], default=os.getenv("SIM_TRANSPORT", "mqtt"))
    p.add_argument("--api", default=os.getenv("API_URL", "http://localhost:8080"), help="backend URL (device discovery + http transport)")
    p.add_argument("--key", default=os.getenv("IOT_API_KEY", "emobility-iot-key"), help="X-IoT-Key")
    p.add_argument("--mqtt-host", default=os.getenv("MQTT_HOST", "localhost"))
    p.add_argument("--mqtt-port", type=int, default=int(os.getenv("MQTT_PORT", "1883")))
    p.add_argument("--tick", type=float, default=float(os.getenv("SIM_TICK", "2")), help="seconds between ticks")
    p.add_argument("--ticks", type=int, default=0, help="stop after N ticks (0 = run forever)")
    p.add_argument("--speed", type=float, default=float(os.getenv("SIM_SPEED", "5")), help="charging time acceleration")
    p.add_argument("--drive-speed", type=float, default=float(os.getenv("SIM_DRIVE_SPEED", "20")), help="driving time acceleration")
    p.add_argument("--plug-delay", type=int, default=2, help="ticks before an ASSIGNED vehicle is plugged in")
    p.add_argument("--fault-rate", type=float, default=float(os.getenv("SIM_FAULT_RATE", "0.0005")), help="probability per port per tick")
    p.add_argument("--repair-ticks", type=int, default=30)
    p.add_argument("--lost-rate", type=float, default=float(os.getenv("SIM_LOST_RATE", "0.001")))
    p.add_argument("--lost-ticks", type=int, default=40, help="ticks without data after a connection loss")
    p.add_argument("--overheat-rate", type=float, default=float(os.getenv("SIM_OVERHEAT_RATE", "0")))
    p.add_argument("--tire-fault-rate", type=float, default=float(os.getenv("SIM_TIRE_FAULT_RATE", "0.0002")))
    p.add_argument("--charging-csv", default=DEFAULT_CHARGING_CSV)
    p.add_argument("--telemetry-csv", default=DEFAULT_TELEMETRY_CSV)
    p.add_argument("--seed", type=int, default=None)
    p.add_argument("-v", "--verbose", action="store_true")
    return p.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    if args.seed is not None:
        random.seed(args.seed)
    charging = ChargingProfile.from_csv(args.charging_csv)
    telemetry = TelemetryProfiles(args.telemetry_csv)
    log(f"charging profile: +{charging.soc_step:.2f}% SOC/step, {charging.current_base:.1f} A, "
        f"{charging.volt_per_soc:.2f} V per % SOC, +{charging.temp_step:.1f} C/step")
    log(f"telemetry profiles: {', '.join(telemetry.ids)} (moving speed {telemetry.moving_speed:.1f} km/h)")

    while True:
        try:
            transport = MqttTransport(args.mqtt_host, args.mqtt_port) if args.transport == "mqtt" else HttpTransport(args.api, args.key)
            break
        except OSError as e:
            log(f"MQTT broker not reachable ({e}), retry in 5 s")
            time.sleep(5)

    sim = Simulator(args, transport, charging, telemetry)
    n = 0
    try:
        while args.ticks == 0 or n < args.ticks:
            started = time.time()
            sim.tick()
            n += 1
            if n % 15 == 0:
                log(f"tick {n}: {sim.sent} messages sent")
            time.sleep(max(0.0, args.tick - (time.time() - started)))
    except KeyboardInterrupt:
        pass
    finally:
        transport.close()
    log(f"stopped after {n} ticks, {sim.sent} messages")
    return 0


if __name__ == "__main__":
    sys.exit(main())

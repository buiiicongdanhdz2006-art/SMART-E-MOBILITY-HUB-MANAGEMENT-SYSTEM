# Sensor sample datasets

Both files are the public **sample rows** shown on IoTDataset.com, topic *Connected Vehicles*
(https://iotdataset.com/topic/connected-vehicles). Licence: CC0 1.0 (public domain) — confirm at the source before
redistributing.

| File | Source record | Original source | Used for |
|---|---|---|---|
| `ev_battery_charging_sample.csv` | https://iotdataset.com/data/ev-battery-charging-performance | Kaggle `programmer3/ev-battery-charging-dataset` | Charging-port sensor: SOC curve, voltage, current, battery temperature |
| `connected_car_telemetry_sample.csv` | https://iotdataset.com/data/vehicle-telemetry-sedan-suv-truck-1k-rows-5d56 | IoTSyn generator (seed 129324716) | Vehicle telemetry: speed, heading, acceleration, tyre pressure, driving events |

How the simulator uses them (`simulator.py`):

* **Charging profile** – the average step change of SOC / voltage / current / temperature between consecutive rows
  (+0.62 % SOC, +1.0 V, −0.17 A, +1.0 °C per step) and their noise are learnt from the CSV. The simulator then
  generates a full charging curve from the request's real battery level: constant current up to 80 % SOC, tapering
  current after that (CC-CV), voltage rising with SOC and temperature rising towards a plateau. `--overheat-rate`
  injects readings above 55 °C so the backend raises an OVERHEAT incident and moves the port to FIXING.
* **Telemetry profile** – each backend vehicle (codes V001…V035) is given the profile of a dataset vehicle
  (V001…V008, round robin). Moving vehicles (IN_USE, IN_TRANSIT) use the dataset speed / heading / acceleration,
  re-projected onto the National University area; parked vehicles report speed 0. Tyre pressure comes from the
  dataset (221–280 kPa); `--tire-fault-rate` injects a low value (< 180 kPa) that the backend turns into a
  VEHICLE_FAULT incident. `Fuel_Level_Pct` is not used because the backend already knows the real battery level.

Only the sample rows are public on IoTDataset.com. To use a full dataset, download it from the original source and
pass it with `--charging-csv` / `--telemetry-csv` (same column names).

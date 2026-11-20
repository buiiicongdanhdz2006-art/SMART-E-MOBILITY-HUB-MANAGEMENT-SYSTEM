# Smart E-Mobility Hub Management System

Bài tập lớn Công nghệ phần mềm (HCMUT, HK261) – hệ thống quản lý Mobility Hub, xe điện dùng chung, bãi đỗ và trạm
sạc trong Khu đô thị ĐHQG-HCM. Source code hiện thực theo thiết kế của **Assignment Part 3** (class diagram chương 6,
kiến trúc / triển khai chương 7).

| Thành phần | Công nghệ | Thư mục |
|---|---|---|
| Backend (REST API + WebSocket) | Java 17, Spring Boot 3.3, Spring Data JPA | `backend/` |
| Web client (User, Operator, Admin) | ReactJS (Vite) | `web/` |
| Mobile client (User) | React Native (Expo) | `mobile/` |
| IoT simulator (F-12) | Python 3 + MQTT (paho) | `iot-simulator/` |
| Database | PostgreSQL 16 (H2 cho profile `local`) | – |
| Message broker | Eclipse Mosquitto | `infra/mosquitto/` |
| Triển khai | Docker Compose | `docker-compose.yml` |

## 1. Chạy hệ thống

### Cách A – Docker Compose (đầy đủ như sơ đồ triển khai)

```bash
docker compose up --build
```

- Web: http://localhost:8081 · API: http://localhost:8080 · MQTT: `localhost:1883` · PostgreSQL: `localhost:5432`
- Container `iot-simulator` tự lấy danh sách thiết bị từ backend và gửi dữ liệu cảm biến qua MQTT.

### Cách B – Chạy từng phần khi phát triển (không cần Docker / PostgreSQL / MQTT)

```bash
# 1) Backend với H2 (file ./backend/data), MQTT tắt
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2) Web (proxy /api và /ws sang :8080)
cd web
npm install
npm run dev            # http://localhost:5173

# 3) IoT simulator gửi dữ liệu qua HTTP thay cho MQTT
cd iot-simulator
python simulator.py --transport http --api http://localhost:8080
```

Mobile: sửa `mobile/config.js` thành IP LAN của máy chạy backend, rồi `cd mobile && npm install && npx expo start` (mở bằng Expo Go).

Khi cập nhật code có thay đổi entity, xoá thư mục `backend/data` (H2) để tạo lại CSDL.

Có Mosquitto chạy ở máy thì bật MQTT bằng `MQTT_ENABLED=true` cho backend và chạy simulator mặc định (`--transport mqtt`).

### Tài khoản demo (tạo tự động lần chạy đầu – `DataSeeder`)

| Vai trò | Đăng nhập | Mật khẩu |
|---|---|---|
| Sinh viên (có xe cá nhân `59X1-123.45`) | `student1@hcmut.edu.vn` hoặc MSSV `2410001` | `User@123` |
| Sinh viên 2 | `student2@hcmut.edu.vn` | `User@123` |
| Operator cấp cao (ALL, SENIOR) | `operator@emobility.vn` | `Operator@123` |
| Operator thường | `operator2@emobility.vn` | `Operator@123` |
| Admin | `admin@emobility.vn` | `Admin@123` |

Dữ liệu mẫu: 5 hub (Ga Metro ĐHQG, KTX khu A, KTX khu B, ĐH Bách Khoa Dĩ An, Thư viện Trung tâm), 50 chỗ đỗ,
22 cổng sạc (khu A/B), 35 xe `V001…V035`. Số xe được chia lệch có chủ đích (Ga Metro thiếu xe, KTX B và Bách Khoa
dư xe) để demo F-08.

## 2. Cấu trúc source (mục 7.3)

```
smart-emobility-hub/
├── backend/src/main/java/vn/edu/hcmut/emobility/
│   ├── auth/          F-01  controller/ service/ entity/ repository/
│   ├── hub/           F-02
│   ├── vehicle/       F-03
│   ├── booking/       F-04
│   ├── parking/       F-05
│   ├── charging/      F-06, F-07  (ChargingService, SmartScheduler, IoTPortMonitor ...)
│   ├── fleet/         F-08
│   ├── incident/      F-09
│   ├── simulation/    F-10
│   ├── notification/  F-11, F-13
│   ├── iot/           F-12  (MQTT subscriber, HTTP ingest, xử lý telemetry)
│   ├── common/        enums, exceptions, security, interface IHubQuery / IVehicleState / IIncidentReport / INotification, events, websocket
│   └── config/        WebConfig, WebSocketConfig, DataSeeder
├── backend/src/test/  unit test
├── web/               ReactJS
├── mobile/            React Native
├── iot-simulator/     Python + sample dataset
├── infra/mosquitto/
└── docker-compose.yml
```

Các module phụ thuộc nhau qua interface đúng như component diagram: Booking/Parking/Charging/Fleet dùng
`IHubQuery`; Booking/Fleet/Incident/IoT dùng `IVehicleState`; Charging/Fleet dùng `IIncidentReport`; mọi module gửi
thông báo qua `INotification`. F-06 không phụ thuộc thuật toán lịch: `ChargingService` phát sự kiện qua
`IChargingEvents`, `SmartScheduler` xử lý (đúng hình 3 – package structure của module charging).

## 3. Đối chiếu thiết kế (chương 6) ↔ code

| Chức năng | Class trong PDF | File chính |
|---|---|---|
| F-01 | AuthController, ProfileController, VehicleController, EmailService, Session, OtpCode, Account/User/Operator/Admin | `auth/controller/*`, `auth/service/AuthService`, `ProfileService`, `EmailService`, `auth/entity/*` |
| F-02 | HubController, AlertController, Hub, OverloadAlert, HubSummary, HubStatus, AlertDetail, Location | `hub/controller/*`, `hub/service/HubService`, `OverloadMonitor`, `hub/entity/*`, `hub/dto/*` |
| F-03 | VehicleService, Vehicle | `vehicle/service/VehicleService`, `vehicle/entity/Vehicle` |
| F-04 | BookingService, Booking, Rental | `booking/*` |
| F-05 | ParkingService, ParkingSlot, ParkingReservation, PersonalVehicle | `parking/*` |
| F-06 | ChargingController, IoTPortMonitor, ChargingService, ChargingRequest, ChargingSession, ChargingPort, WaitingQueue, ChargingRequestRepository, ChargingPortRepository, ChargingRequestForm | `charging/controller/*`, `charging/service/ChargingService`, `charging/entity/*`, `charging/repository/*`, `charging/dto/*` |
| F-07 | ScheduleController, SmartScheduler, ChargingSchedule, PortAssignment, ScheduleRepository, ScheduleParams | `charging/controller/ScheduleController`, `charging/service/SmartScheduler`, `charging/entity/ChargingSchedule`, `PortAssignment` |
| F-08 | FleetDispatchService, FleetFilter, FleetStatistics, HubImbalance, RedistributionSuggestion, DispatchRequest, DispatchOrder, ImbalanceDetector, RedistributionRecommender | `fleet/*` |
| F-09 | IncidentMaintenanceService, Incident, IncidentStatusLog, IncidentReportRequest, IncidentFilter, IncidentRepository | `incident/*` |
| F-10 | SimulationController, SimulationEngine, HubDatabase, Scenario, TimeWindow, ValidationResult, HubState, SimulationResult, ImpactMetrics, SuggestedAction | `simulation/*` (SimulationUI = trang `web/src/pages/SimulationPage.jsx`) |
| F-11 | AlertService, NotificationService, CaseDatabase, Case, Alert, Complaint | `notification/*` (MonitoringDashboard = `MonitoringDashboardController` + `web/src/pages/MonitoringPage.jsx`) |
| F-12 | IoTPortMonitor, MQTT subscriber | `iot/MqttSubscriber`, `iot/IoTDataService`, `iot/VehicleTelemetryHandler`, `iot/IoTIngestController` |
| F-13 | NotificationService (tự động) | `notification/service/AutoNotificationJob` + thông báo pin thấp (IoT), sạc xong (ChargingService) |

Ghi chú hiện thực:
- Các phương thức static trong class diagram (`Account.find`, `Hub.findAll`, `OverloadAlert.findByStatus` …) được đặt ở
  repository Spring Data tương ứng (`AccountRepository.find`, `HubRepository.findAll` …).
- `NotificationService` xuất hiện ở 3 class diagram (F-07, F-09, F-11) được gộp thành một class dùng chung.
- Không double-booking: khoá dòng (`PESSIMISTIC_WRITE`) trên xe khi đặt / điều phối, trên các chỗ đỗ của hub khi đặt
  chỗ, trên các cổng sạc của hub khi gán cổng; một cổng chỉ giữ một yêu cầu (`reservedRequestId`).
- Bảo mật: mật khẩu băm PBKDF2-SHA256 + salt, phiên đăng nhập bằng token, kiểm tra vai trò / quyền Operator ở mọi API.

## 4. F-06 / F-07 – Sạc và lập lịch sạc thông minh

Trạng thái theo state chart:

- `ChargingRequest`: CREATED → ASSIGNED | WAITING → ASSIGNED → CHARGING → COMPLETED; ASSIGNED quá 15 phút chưa cắm → EXPIRED; WAITING/ASSIGNED → CANCELLED; cổng hỏng → về WAITING (đầu hàng chờ).
- `ChargingPort`: AVAILABLE ⇄ CHARGING, bất kỳ → FIXING → AVAILABLE (cổng không bị xoá).
- `ChargingSession`: START → CHARGING → READY, hoặc INTERRUPTED khi cổng hỏng; mất dữ liệu cảm biến → `dataInterrupted` (hiển thị số liệu gần nhất kèm cảnh báo).

Độ ưu tiên (`SmartScheduler.calculatePriority`, ngưỡng chỉnh được trong `ScheduleParams`):

| Mức | Điều kiện |
|---|---|
| HIGH | Khẩn cấp (Priority Override, bắt buộc lý do) · bị gián đoạn do cổng hỏng · dùng xe trong ≤ 60 phút · pin ≤ 20 % |
| MEDIUM | dùng xe trong ≤ 180 phút · pin ≤ 50 % · thời gian sạc ≥ 90 phút |
| LOW | còn lại |

Hàng chờ sắp theo: khẩn cấp → yêu cầu bị gián đoạn do cổng hỏng → mức ưu tiên → giờ dùng xe gần nhất → pin thấp nhất → nhu cầu sạc lớn nhất → đến trước.
Yêu cầu đăng ký cho giờ sau chỉ được gán cổng khi còn ≤ 15 phút đến giờ bắt đầu (job mỗi phút `dispatchReadyRequests`).
Lập lịch không ngắt xe đang sạc (non-preemptive); lịch gồm các cổng **gán ngay** và giờ sạc **dự kiến** của từng
yêu cầu đang chờ (tính từ giờ kết thúc dự kiến của các phiên đang chạy). Tính lại lịch khi: có yêu cầu HIGH mới,
Operator đánh dấu khẩn cấp hoặc bấm tính lại, cổng hỏng / sửa xong, yêu cầu bị huỷ / hết hạn, phiên sạc kết thúc.

Kịch bản demo nhanh (web + simulator):
1. Đăng nhập `student1` → *Sạc xe* → chọn Ga Metro, khu A, pin 25 %, mục tiêu 90 % → *Gửi yêu cầu* → được gán cổng.
2. Simulator tự “cắm xe” sau ~4 giây → trạng thái CHARGING, thanh pin tăng theo thời gian thực (WebSocket).
3. Đăng nhập `operator` → *Lịch sạc* → *Báo hỏng* cổng đang sạc → yêu cầu về đầu hàng chờ, phiên INTERRUPTED, lịch được tính lại.
4. Tạo thêm nhiều yêu cầu (vd. từ Operator với xe dùng chung) để có hàng chờ → *Khẩn cấp* với lý do → yêu cầu lên vị trí 1.

## 5. IoT simulator và dataset (F-12)

Dữ liệu cảm biến được sinh từ 2 bộ **sample** của IoTDataset.com, chủ đề Connected Vehicles
(https://iotdataset.com/topic/connected-vehicles), giấy phép CC0 – chi tiết trong `iot-simulator/data/README.md`:

- `ev_battery_charging_sample.csv` (EV Battery Charging & Performance) → đường cong SOC, điện áp, dòng, nhiệt độ của cổng sạc.
- `connected_car_telemetry_sample.csv` (Synthetic Connected Car IoT) → tốc độ, hướng, gia tốc, áp suất lốp, sự kiện lái của xe.

Topic MQTT (cũng dùng được qua `POST /api/iot/events` với header `X-IoT-Key`):

| Topic | Payload |
|---|---|
| `emobility/ports/{portCode}/plugged` | `{"vehicleId": "59X1-123.45"}` |
| `emobility/ports/{portCode}/battery` | `{"soc": 46.2, "voltage": 382.5, "current": 10.0, "temperature": 27.3}` |
| `emobility/ports/{portCode}/status` | `{"status": "FIXING"}` hoặc `AVAILABLE` |
| `emobility/ports/{portCode}/lost` | `{}` |
| `emobility/vehicles/{vehicleCode}/telemetry` | `{"battery": 81, "lat": 10.87, "lng": 106.80, "speed": 12.4, "tirePressure": 238.6, "event": "Normal"}` |
| `emobility/slots/{slotCode}/status` | `{"occupied": true}` |

Tuỳ chọn hay dùng: `--speed` (tăng tốc sạc), `--fault-rate` (hỏng cổng ngẫu nhiên), `--lost-rate` (mất kết nối),
`--overheat-rate` (quá nhiệt > 55 °C → sự cố OVERHEAT), `--tire-fault-rate` (lốp non → sự cố VEHICLE_FAULT),
`--charging-csv` / `--telemetry-csv` (dùng bộ dataset đầy đủ tải từ nguồn gốc), `-v` (in từng bản tin).

## 6. Kiểm thử

```bash
cd backend && mvn test
```

- `ChargingFlowIntegrationTest` (`@SpringBootTest`, H2 trong bộ nhớ, profile `test`): khởi động toàn bộ ứng dụng
  (Spring, JPA, Tomcat) rồi chạy luồng F-06/F-07 qua service + repository thật — gán cổng, hàng chờ, cắm sạc, sạc xong
  nhường cổng, cổng hỏng / sửa xong, huỷ — và gọi thử các API công khai (JSON, 401, 404). Nếu ánh xạ JPA hoặc query sai,
  test này báo lỗi đầu tiên.

Unit test (JUnit 5) cho các luật nghiệp vụ không cần database:
`ChargingRequestStateTest` (state chart yêu cầu / phiên / cổng sạc), `SmartSchedulerPriorityTest` (độ ưu tiên, hàng chờ,
yêu cầu bị gián đoạn F-07), `FleetLogicTest` (mất cân bằng, gợi ý điều phối F-08), `SimulationEngineTest` (4 loại kịch bản F-10),
`DomainRulesTest` (băm mật khẩu, validate đăng ký, mức tải hub, state chart xe / sự cố / cảnh báo, quyền Operator).

## 7. API chính

| Nhóm | Endpoint |
|---|---|
| Auth (F-01) | `POST /api/auth/register · login · logout · otp · reset-password`, `GET/PUT /api/profile`, `GET /api/profile/history`, `POST /api/profile/vehicles` |
| Hub (F-02) | `GET /api/hubs`, `/api/hubs/{id}/status`, `/api/hubs/nearest?lat&lng&need`, `GET /api/alerts/overload`, `POST /api/alerts/overload/{id}/resolve` |
| Xe (F-03) | `GET /api/vehicles`, `GET /api/vehicles/{id}`, `POST/PUT /api/vehicles` (Admin) |
| Thuê xe (F-04) | `GET /api/bookings/search`, `POST /api/bookings`, `POST /api/bookings/{id}/pickup · cancel`, `POST /api/rentals/{id}/return` |
| Đỗ xe (F-05) | `GET /api/parking/hubs/{id}/slots`, `POST /api/parking/reservations`, `POST .../{id}/check-in · check-out · cancel` |
| Sạc (F-06) | `GET /api/charging/hubs/{id}/ports · queue · active`, `POST /api/charging/requests`, `GET .../requests/my`, `GET .../requests/{id}/status`, `POST .../{id}/stop · cancel`, `POST /api/charging/ports/{id}/fault` |
| Lịch sạc (F-07) | `POST /api/schedule/generate`, `POST /api/schedule/requests/{id}/urgent`, `POST /api/schedule/hubs/{id}/reschedule`, `GET /api/schedule/hubs/{id}/latest`, `POST /api/schedule/ports/{id}/status` |
| Đội xe (F-08) | `GET /api/fleet/statistics · vehicles · imbalances · recommendations`, `POST /api/fleet/dispatch-orders`, `GET .../active`, `POST .../{id}/refresh · arrivals/{vehicleId} · incidents`, `PUT /api/fleet/vehicles/{id}/status` |
| Sự cố (F-09) | `POST/GET /api/incidents`, `POST /api/incidents/{id}/investigate · invalid · maintenance · resolve` |
| Mô phỏng (F-10) | `POST /api/simulation/scenarios`, `GET /api/simulation/rerun · scenarios · status` |
| Giám sát (F-11) | `GET /api/monitoring/hubs/{id} · cases · channels`, `POST /api/monitoring/alerts`, `POST .../alerts/{id}/retry`, `POST .../cases/{id}/actions · escalate · resolve`, `POST /api/complaints` |
| Thông báo (F-13) | `GET /api/notifications`, `POST /api/notifications/{id}/read`, WebSocket `/ws` |
| IoT (F-12) | `POST /api/iot/events`, `GET /api/iot/devices` (header `X-IoT-Key`) |

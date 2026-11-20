package vn.edu.hcmut.emobility.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Admin;
import vn.edu.hcmut.emobility.auth.entity.Operator;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.WaitingQueueRepository;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.security.PasswordHasher;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.parking.entity.ParkingSlot;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;
import vn.edu.hcmut.emobility.parking.repository.ParkingSlotRepository;
import vn.edu.hcmut.emobility.parking.repository.PersonalVehicleRepository;
import vn.edu.hcmut.emobility.vehicle.entity.Vehicle;
import vn.edu.hcmut.emobility.vehicle.repository.VehicleRepository;

/**
 * Demo data of the National University urban area (runs once, when the database has no account).
 * Vehicle codes V001.. match the Vehicle_ID of the connected-car sample dataset used by the IoT simulator.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AccountRepository accounts;
    private final HubRepository hubs;
    private final ParkingSlotRepository slots;
    private final ChargingPortRepository ports;
    private final WaitingQueueRepository queues;
    private final VehicleRepository vehicles;
    private final PersonalVehicleRepository personalVehicles;
    private final boolean enabled;

    public DataSeeder(AccountRepository accounts, HubRepository hubs, ParkingSlotRepository slots, ChargingPortRepository ports,
                      WaitingQueueRepository queues, VehicleRepository vehicles, PersonalVehicleRepository personalVehicles,
                      @Value("${app.seed.enabled:true}") boolean enabled) {
        this.accounts = accounts;
        this.hubs = hubs;
        this.slots = slots;
        this.ports = ports;
        this.queues = queues;
        this.vehicles = vehicles;
        this.personalVehicles = personalVehicles;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || accounts.count() > 0) return;
        log.info("Seeding demo data...");

        accounts.save(new Admin("admin@emobility.vn", "0900000001", "Quản trị viên", PasswordHasher.hash("Admin@123")));
        accounts.save(new Operator("operator@emobility.vn", "0900000002", "Operator cấp cao", PasswordHasher.hash("Operator@123"), "ALL,SENIOR"));
        accounts.save(new Operator("operator2@emobility.vn", "0900000003", "Operator ca ngày", PasswordHasher.hash("Operator@123"),
                "MANAGE_CHARGING,VIEW_FLEET,DISPATCH,MANAGE_INCIDENT,ISSUE_ALERT,MANAGE_COMPLAINT,RUN_SIMULATION"));
        User s1 = (User) accounts.save(new User("student1@hcmut.edu.vn", "0912345678", "2410001", "Nguyễn Văn An", PasswordHasher.hash("User@123")));
        User s2 = (User) accounts.save(new User("student2@hcmut.edu.vn", "0912345679", "2410002", "Trần Thị Bình", PasswordHasher.hash("User@123")));
        personalVehicles.save(new PersonalVehicle(s1, VehicleType.E_MOTORBIKE, "59X1-123.45", "VinFast Evo200"));
        personalVehicles.save(new PersonalVehicle(s2, VehicleType.E_MOTORBIKE, "59X2-678.90", "Yadea Odora"));

        //            code   name                                   address                                   lat       lng       demand vehicles ports
        Hub h1 = hub("H01", "Ga Metro Đại học Quốc gia", "Ga Metro số 1, Linh Trung, Thủ Đức", 10.8660, 106.8017, 10, 4, 6);
        Hub h2 = hub("H02", "Ký túc xá Khu A", "Đường Tạ Quang Bửu, Linh Trung, Thủ Đức", 10.8786, 106.8065, 6, 6, 4);
        Hub h3 = hub("H03", "Ký túc xá Khu B", "Đường Mạc Đĩnh Chi, Đông Hoà, Dĩ An", 10.8822, 106.7826, 6, 12, 4);
        Hub h4 = hub("H04", "ĐH Bách Khoa - cơ sở Dĩ An", "Khu phố Tân Lập, Đông Hoà, Dĩ An", 10.8807, 106.8053, 5, 9, 4);
        Hub h5 = hub("H05", "Thư viện Trung tâm ĐHQG", "Khu phố 6, Linh Trung, Thủ Đức", 10.8760, 106.8018, 4, 4, 4);

        VehicleType[] types = {VehicleType.E_BIKE, VehicleType.E_MOTORBIKE, VehicleType.E_BIKE, VehicleType.E_MOTORBIKE, VehicleType.E_CAR};
        String[] models = {"VinFast Klara S", "Yadea G5", "Dat Bike Weaver", "VinFast Feliz", "VinFast VF3"};
        int[] batteries = {50, 54, 48, 81, 90, 61, 77, 46, 95, 33, 18, 72, 66, 88, 57, 92, 41, 85, 63, 79, 15, 70, 99, 52, 68, 87, 44, 91, 58, 74, 83, 39, 96, 62, 80};
        Hub[] owners = new Hub[35];
        int idx = 0;
        for (Hub h : new Hub[]{h1, h2, h3, h4, h5}) {
            int n = switch (h.getCode()) { case "H01" -> 4; case "H02" -> 6; case "H03" -> 12; case "H04" -> 9; default -> 4; };
            for (int i = 0; i < n; i++) owners[idx++] = h;
        }
        for (int i = 0; i < 35; i++) {
            vehicles.save(new Vehicle(String.format("V%03d", i + 1), types[i % types.length], models[i % models.length], owners[i], batteries[i]));
        }
        log.info("Seed done: 5 hubs, 35 vehicles, accounts admin/operator/student (see README)");
    }

    private Hub hub(String code, String name, String address, double lat, double lng, int demand, int vehicles, int nPorts) {
        Hub h = hubs.save(new Hub(code, name, address, lat, lng, demand));
        queues.save(new WaitingQueue(h));
        for (int i = 1; i <= 10; i++) slots.save(new ParkingSlot(String.format("%s-S%02d", code, i), h));
        for (int i = 1; i <= nPorts; i++) {
            String area = i <= nPorts / 2 ? "A" : "B";
            ports.save(new ChargingPort(String.format("%s-P%02d", code, i), h, area, i % 2 == 0 ? 11.0 : 7.0));
        }
        return h;
    }
}

package vn.edu.hcmut.emobility.charging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.charging.dto.ChargingRequestForm;
import vn.edu.hcmut.emobility.charging.dto.ChargingRequestView;
import vn.edu.hcmut.emobility.charging.entity.ChargingPort;
import vn.edu.hcmut.emobility.charging.entity.ChargingRequest;
import vn.edu.hcmut.emobility.charging.entity.WaitingQueue;
import vn.edu.hcmut.emobility.charging.repository.ChargingPortRepository;
import vn.edu.hcmut.emobility.charging.repository.ChargingRequestRepository;
import vn.edu.hcmut.emobility.charging.repository.WaitingQueueRepository;
import vn.edu.hcmut.emobility.charging.service.ChargingService;
import vn.edu.hcmut.emobility.charging.service.SmartScheduler;
import vn.edu.hcmut.emobility.common.enums.PortStatus;
import vn.edu.hcmut.emobility.common.enums.RequestStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.security.PasswordHasher;
import vn.edu.hcmut.emobility.hub.entity.Hub;
import vn.edu.hcmut.emobility.hub.repository.HubRepository;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;
import vn.edu.hcmut.emobility.parking.repository.PersonalVehicleRepository;

/**
 * Starts the whole application (Spring context, JPA on H2, Tomcat) and runs the main F-06 / F-07 flow
 * through the real services and repositories. If the context cannot start (bad mapping, bad query),
 * this test fails first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChargingFlowIntegrationTest {

    @Autowired HubRepository hubs;
    @Autowired WaitingQueueRepository queues;
    @Autowired ChargingPortRepository ports;
    @Autowired ChargingRequestRepository requests;
    @Autowired AccountRepository accounts;
    @Autowired PersonalVehicleRepository personalVehicles;
    @Autowired ChargingService chargingService;
    @Autowired SmartScheduler scheduler;
    @Autowired TestRestTemplate http;

    @Test
    void fullChargingFlow() {
        Hub hub = hubs.save(new Hub("T01", "Test hub", "addr", 10.866, 106.8017, 5));
        queues.save(new WaitingQueue(hub));
        ChargingPort a1 = ports.save(new ChargingPort("T01-P01", hub, "A", 7.0));
        ports.save(new ChargingPort("T01-P02", hub, "B", 7.0));
        User u1 = accounts.save(new User("it1@hcmut.edu.vn", "0911111111", "2419001", "IT 1", PasswordHasher.hash("123456")));
        User u2 = accounts.save(new User("it2@hcmut.edu.vn", "0911111112", "2419002", "IT 2", PasswordHasher.hash("123456")));
        PersonalVehicle v1 = personalVehicles.save(new PersonalVehicle(u1, VehicleType.E_MOTORBIKE, "59T1-111.11", "VF"));
        PersonalVehicle v2 = personalVehicles.save(new PersonalVehicle(u2, VehicleType.E_MOTORBIKE, "59T2-222.22", "VF"));

        // UC-F06-02 / 03: u1 gets the only port of area A, u2 waits
        ChargingRequestView r1 = chargingService.createRequest(new ChargingRequestForm(null, v1.getId(), hub.getId(), "A", null, 60, 30, 90, null), u1);
        assertEquals(RequestStatus.ASSIGNED, r1.status());
        assertEquals("T01-P01", r1.portCode());
        ChargingRequestView r2 = chargingService.createRequest(new ChargingRequestForm(null, v2.getId(), hub.getId(), "A", null, 60, 40, 90, null), u2);
        assertEquals(RequestStatus.WAITING, r2.status());
        assertEquals(Integer.valueOf(1), r2.queuePosition());

        // UC-F06-04: plug in, battery readings, completion -> port goes to u2
        chargingService.startCharging(a1.getId(), "59T1-111.11");
        assertEquals(RequestStatus.CHARGING, requests.findById(r1.id()).orElseThrow().getStatus());
        chargingService.handleBatteryUpdate(a1.getId(), 60, 395.0, 10.0, 30.0);
        chargingService.handleBatteryUpdate(a1.getId(), 90, 410.0, 4.0, 33.0);
        assertEquals(RequestStatus.COMPLETED, requests.findById(r1.id()).orElseThrow().getStatus());
        ChargingRequest q2 = requests.findById(r2.id()).orElseThrow();
        assertEquals(RequestStatus.ASSIGNED, q2.getStatus());
        assertEquals("T01-P01", q2.getAssignedPort().getCode());

        // UC-F07-03: port broken -> u2 back to WAITING (served first), repaired -> u2 assigned again
        chargingService.reportPortFault(a1.getId(), "Không nhận sạc", u1);
        assertEquals(PortStatus.FIXING, ports.findById(a1.getId()).orElseThrow().getStatus());
        q2 = requests.findById(r2.id()).orElseThrow();
        assertEquals(RequestStatus.WAITING, q2.getStatus());
        assertTrue(q2.isResumeFirst());
        scheduler.portStatusChanged(ports.findById(a1.getId()).orElseThrow(), PortStatus.AVAILABLE);
        assertEquals(RequestStatus.ASSIGNED, requests.findById(r2.id()).orElseThrow().getStatus());

        // UC-F06-05: cancel frees the port
        chargingService.cancelRequest(r2.id(), u2);
        assertEquals(RequestStatus.CANCELLED, requests.findById(r2.id()).orElseThrow().getStatus());
        assertTrue(ports.findById(a1.getId()).orElseThrow().isFree());
        assertFalse(chargingService.getRequestsByUser(u1.getId()).isEmpty());
    }

    @Test
    void publicEndpointsRespondWithJson() {
        Hub hub = hubs.save(new Hub("T02", "Hub JSON", "addr", 10.87, 106.80, 3));
        queues.save(new WaitingQueue(hub));
        ports.save(new ChargingPort("T02-P01", hub, "A", 11.0));
        ResponseEntity<String> list = http.getForEntity("/api/hubs", String.class);
        assertEquals(200, list.getStatusCode().value());
        assertTrue(list.getBody().contains("\"T02\""));
        ResponseEntity<String> portList = http.getForEntity("/api/charging/hubs/" + hub.getId() + "/ports", String.class);
        assertEquals(200, portList.getStatusCode().value());
        assertTrue(portList.getBody().contains("T02-P01"));
        ResponseEntity<String> status = http.getForEntity("/api/hubs/" + hub.getId() + "/status", String.class);
        assertEquals(200, status.getStatusCode().value());
        assertEquals(401, http.getForEntity("/api/charging/requests/my", String.class).getStatusCode().value());
        assertEquals(404, http.getForEntity("/api/khong-ton-tai", String.class).getStatusCode().value());
    }
}

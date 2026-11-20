package vn.edu.hcmut.emobility;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Smart E-Mobility Hub Management System - backend (REST API + WebSocket + MQTT). */
@SpringBootApplication
@EnableScheduling
public class EmobilityApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(EmobilityApplication.class, args);
    }
}

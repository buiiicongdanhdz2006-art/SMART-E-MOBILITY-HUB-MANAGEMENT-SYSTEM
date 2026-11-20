package vn.edu.hcmut.emobility.parking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.common.enums.VehicleType;

/** Personal electric vehicle registered by a student (UC-F01-08), used for parking and charging. */
@Entity
@Table(name = "personal_vehicles")
public class PersonalVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    private VehicleType type;

    @Column(nullable = false, unique = true)
    private String plate;

    private String brand;
    private Integer batteryLevel;
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PersonalVehicle() {}

    public PersonalVehicle(User owner, VehicleType type, String plate, String brand) {
        this.owner = owner;
        this.type = type;
        this.plate = plate;
        this.brand = brand;
    }

    public Long getId() { return id; }
    @JsonIgnore
    public User getOwner() { return owner; }
    public Long getOwnerId() { return owner == null ? null : owner.getId(); }
    public VehicleType getType() { return type; }
    public String getPlate() { return plate; }
    public String getBrand() { return brand; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

package vn.edu.hcmut.emobility.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import vn.edu.hcmut.emobility.common.enums.Role;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;

/**
 * Student / User. A user that has registered at least one personal vehicle plays the
 * "Vehicle owner" role (PersonalVehicleOwner extends User in the design).
 * Bookings of the user are read through BookingRepository (getActiveBookings / getEndedBookings).
 */
@Entity
@DiscriminatorValue("USER")
public class User extends Account {

    @JsonIgnore
    @OneToMany(mappedBy = "owner")
    private List<PersonalVehicle> personalVehicles = new ArrayList<>();

    protected User() {}

    public User(String email, String phone, String studentId, String fullName, String passwordHash) {
        super(email, phone, studentId, fullName, passwordHash);
    }

    @Override
    public Role getRole() { return Role.USER; }

    @JsonIgnore
    public List<PersonalVehicle> getVehicles() { return personalVehicles; }

    public void addVehicle(PersonalVehicle vehicle) { personalVehicles.add(vehicle); }

    /** PersonalVehicleOwner role. */
    @JsonIgnore
    public boolean isVehicleOwner() { return !personalVehicles.isEmpty(); }
}

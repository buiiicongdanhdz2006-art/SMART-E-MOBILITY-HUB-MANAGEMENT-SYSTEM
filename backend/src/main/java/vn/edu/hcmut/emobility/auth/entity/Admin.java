package vn.edu.hcmut.emobility.auth.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import vn.edu.hcmut.emobility.common.enums.Role;

@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends Account {

    protected Admin() {}

    public Admin(String email, String phone, String fullName, String passwordHash) {
        super(email, phone, null, fullName, passwordHash);
    }

    @Override
    public Role getRole() { return Role.ADMIN; }
}

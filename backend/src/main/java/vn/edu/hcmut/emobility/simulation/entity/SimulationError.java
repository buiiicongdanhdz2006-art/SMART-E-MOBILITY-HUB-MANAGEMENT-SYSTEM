package vn.edu.hcmut.emobility.simulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Simulation error stored by HubDatabase.logError() (F10-AF-02). */
@Entity
@Table(name = "simulation_errors")
public class SimulationError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String source;

    @Column(length = 2000)
    private String message;

    private LocalDateTime at = LocalDateTime.now();

    protected SimulationError() {}

    public SimulationError(String source, String message) {
        this.source = source;
        this.message = message;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getMessage() { return message; }
    public LocalDateTime getAt() { return at; }
}

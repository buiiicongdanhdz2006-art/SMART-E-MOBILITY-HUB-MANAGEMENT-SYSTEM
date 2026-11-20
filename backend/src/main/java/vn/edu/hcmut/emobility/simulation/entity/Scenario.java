package vn.edu.hcmut.emobility.simulation.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.edu.hcmut.emobility.common.enums.ScenarioType;
import vn.edu.hcmut.emobility.common.enums.Severity;

/** What-if scenario configured by an Operator (F-10). */
@Entity
@Table(name = "scenarios")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ScenarioType type;

    private Long hubId;

    @Embedded
    private TimeWindow window;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scenario_params", joinColumns = @JoinColumn(name = "scenario_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    private Map<String, String> parameters = new LinkedHashMap<>();

    private Long createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private Severity resultSeverity;

    @Column(length = 2000)
    private String resultSummary;

    protected Scenario() {}

    public Scenario(ScenarioType type, Long hubId, TimeWindow window, Long createdBy) {
        this.type = type;
        this.hubId = hubId;
        this.window = window;
        this.createdBy = createdBy;
    }

    /** Value of the parameter, or null. */
    public Object getParameter(String key) { return parameters.get(key); }

    public Double getNumber(String key) {
        String v = parameters.get(key);
        if (v == null || v.isBlank()) return null;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
    }

    public void setParameter(String key, Object value) {
        if (value == null) parameters.remove(key); else parameters.put(key, value.toString());
    }

    /** Copy used to prefill the form during Rerun, without modifying the original (F10-AF-03). */
    public Scenario copy() {
        Scenario s = new Scenario(type, hubId, window == null ? null : new TimeWindow(window.getStart(), window.getEnd()), createdBy);
        s.parameters.putAll(parameters);
        return s;
    }

    public void recordResult(Severity severity, String summary) {
        this.resultSeverity = severity;
        this.resultSummary = summary;
    }

    public Long getId() { return id; }
    public ScenarioType getType() { return type; }
    public Long getHubId() { return hubId; }
    public TimeWindow getWindow() { return window; }
    public Map<String, String> getParameters() { return parameters; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Severity getResultSeverity() { return resultSeverity; }
    public String getResultSummary() { return resultSummary; }
}

package vn.edu.hcmut.emobility.simulation.entity;

import java.util.ArrayList;
import java.util.List;

/** Result of a simulation: simulated state, impact metrics and suggested actions. */
public class SimulationResult {

    private Scenario scenario;
    private HubState before;
    private HubState after;
    private ImpactMetrics metrics;
    private final List<SuggestedAction> suggestions = new ArrayList<>();
    private String error;

    public static SimulationResult success(Scenario scenario, HubState before, HubState after, ImpactMetrics metrics) {
        SimulationResult r = new SimulationResult();
        r.scenario = scenario;
        r.before = before;
        r.after = after;
        r.metrics = metrics;
        return r;
    }

    public static SimulationResult failure(Scenario scenario, String error) {
        SimulationResult r = new SimulationResult();
        r.scenario = scenario;
        r.error = error;
        return r;
    }

    public void addSuggestions(List<SuggestedAction> list) {
        suggestions.addAll(list);
        suggestions.sort(null);
    }

    /** True if the simulation completed without errors. */
    public boolean isSuccessful() { return error == null && metrics != null; }

    public Scenario getScenario() { return scenario; }
    public HubState getBefore() { return before; }
    public HubState getAfter() { return after; }
    public ImpactMetrics getMetrics() { return metrics; }
    public List<SuggestedAction> getSuggestions() { return suggestions; }
    public String getError() { return error; }
    public String getSummary() { return metrics == null ? null : metrics.toSummary(); }
}

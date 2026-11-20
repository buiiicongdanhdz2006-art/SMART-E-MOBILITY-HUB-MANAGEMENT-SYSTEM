package vn.edu.hcmut.emobility.simulation.entity;

/** Advisory action generated from the impact metrics (never executed automatically - F10-POST-05). */
public record SuggestedAction(int priority, String title, String detail) implements Comparable<SuggestedAction> {

    /** Higher priority first. */
    @Override
    public int compareTo(SuggestedAction other) { return Integer.compare(other.priority, priority); }
}

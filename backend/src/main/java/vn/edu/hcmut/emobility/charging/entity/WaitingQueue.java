package vn.edu.hcmut.emobility.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import vn.edu.hcmut.emobility.hub.entity.Hub;

/**
 * Waiting queue of a Hub (composition of Hub, aggregates ChargingRequest).
 * Position 1 is the next request to receive a port.
 */
@Entity
@Table(name = "waiting_queues")
public class WaitingQueue {

    /**
     * Order used by reorder(): urgent first, then requests interrupted by a broken port, then HIGH &gt; MEDIUM &gt; LOW, then nearest usage time,
     * lowest battery, highest charging demand, and finally first come first served.
     */
    public static final Comparator<ChargingRequest> PRIORITY_ORDER = Comparator
            .comparing((ChargingRequest r) -> !r.isUrgent())
            .thenComparing(r -> !r.isResumeFirst())
            .thenComparing(r -> r.getPriority().ordinal())
            .thenComparing(r -> r.getNextUsageTime() == null ? LocalDateTime.MAX : r.getNextUsageTime())
            .thenComparingInt(ChargingRequest::getBatteryLevel)
            .thenComparing(Comparator.comparingInt(ChargingRequest::getExpectedDuration).reversed())
            .thenComparing(ChargingRequest::getCreatedAt);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "hub_id", unique = true)
    private Hub hub;

    @JsonIgnore
    @OneToMany
    @JoinColumn(name = "queue_id")
    @OrderColumn(name = "queue_pos")
    private List<ChargingRequest> requests = new ArrayList<>();

    protected WaitingQueue() {}

    public WaitingQueue(Hub hub) {
        this.hub = hub;
    }

    /** Adds the request at the end of the queue (if not already in it) and returns its position. */
    public int enqueue(ChargingRequest request) {
        if (!contains(request)) requests.add(request);
        return getPosition(request);
    }

    /** Removes the request when it is cancelled or receives a port. */
    public void remove(ChargingRequest request) {
        requests.removeIf(r -> same(r, request));
    }

    /** Current position starting at 1, or -1 if the request is not in the queue. */
    public int getPosition(ChargingRequest request) {
        for (int i = 0; i < requests.size(); i++) {
            if (same(requests.get(i), request)) return i + 1;
        }
        return -1;
    }

    /** Moves an urgent request to the next processing position (UC-F07-02). */
    public void moveToNext(ChargingRequest request) {
        remove(request);
        requests.add(0, request);
    }

    /** Sorts the queue again after priorities have been recalculated (UC-F07-03). */
    public void reorder() {
        List<ChargingRequest> sorted = new ArrayList<>(requests);
        sorted.sort(PRIORITY_ORDER);
        requests.clear();
        requests.addAll(sorted);
    }

    public List<ChargingRequest> getAll() { return Collections.unmodifiableList(requests); }

    public int size() { return requests.size(); }

    public boolean contains(ChargingRequest request) { return getPosition(request) > 0; }

    private static boolean same(ChargingRequest a, ChargingRequest b) {
        if (a == b) return true;
        return a.getId() != null && a.getId().equals(b.getId());
    }

    public Long getId() { return id; }
    @JsonIgnore
    public Hub getHub() { return hub; }
}

package vn.edu.hcmut.emobility.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmut.emobility.common.enums.SeverityLevel;
import vn.edu.hcmut.emobility.fleet.dto.DispatchRequest;
import vn.edu.hcmut.emobility.fleet.dto.HubImbalance;
import vn.edu.hcmut.emobility.fleet.dto.RedistributionSuggestion;
import vn.edu.hcmut.emobility.fleet.service.ImbalanceDetector;
import vn.edu.hcmut.emobility.fleet.service.RedistributionRecommender;

/** Imbalance detection and redistribution of F-08 (UC-F08-02, 03, 04). */
class FleetLogicTest {

    private final ImbalanceDetector detector = new ImbalanceDetector();
    private final RedistributionRecommender recommender = new RedistributionRecommender();

    @Test
    void detectsShortageAndSurplusOutsideTolerance() {
        List<HubImbalance> result = detector.detectFrom(List.of(
                HubImbalance.of(1L, "Metro", 4, 10),   // -6 shortage
                HubImbalance.of(2L, "KTX A", 6, 6),    // balanced
                HubImbalance.of(3L, "KTX B", 12, 6),   // +6 surplus
                HubImbalance.of(4L, "BK", 6, 5)));     // +1 within tolerance
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(i -> i.hubId() == 1L && i.isShortage()));
        assertTrue(result.stream().anyMatch(i -> i.hubId() == 3L && !i.isShortage()));
    }

    @Test
    void severityFromGapRatio() {
        assertEquals(SeverityLevel.HIGH, HubImbalance.of(1L, "x", 4, 10).calculateSeverity());   // 0.6
        assertEquals(SeverityLevel.CRITICAL, HubImbalance.of(1L, "x", 1, 10).calculateSeverity()); // 0.9
        assertEquals(SeverityLevel.LOW, HubImbalance.of(1L, "x", 8, 10).calculateSeverity());     // 0.2
    }

    @Test
    void recommendKeepsMinimumReserveAtSource() {
        HubImbalance shortage = HubImbalance.of(1L, "Metro", 0, 10);
        HubImbalance surplus = HubImbalance.of(3L, "KTX B", 5, 1);
        // surplus gap 4, but must keep 2 vehicles -> 3 can move
        assertEquals(3, recommender.calculateTransferQuantity(shortage, surplus));
    }

    @Test
    void recommendSplitsShortageOverSeveralSurplusHubs() {
        List<RedistributionSuggestion> s = recommender.recommend(List.of(
                HubImbalance.of(1L, "Metro", 4, 10),
                HubImbalance.of(3L, "KTX B", 12, 6),
                HubImbalance.of(4L, "BK", 9, 5)));
        int total = s.stream().mapToInt(RedistributionSuggestion::quantity).sum();
        assertEquals(6, total);
        assertEquals(3L, s.get(0).fromHubId()); // biggest surplus first
        assertEquals(1L, s.get(0).toHubId());
        assertEquals(1L, s.get(0).toDispatchRequest().toHubId());
    }

    @Test
    void dispatchRequestValidation() {
        assertFalse(new DispatchRequest(1L, 1L, 2, null, null, null).validate().isEmpty());
        assertFalse(new DispatchRequest(1L, 2L, 0, null, null, null).validate().isEmpty());
        assertFalse(new DispatchRequest(1L, 2L, 2, List.of(5L), null, null).validate().isEmpty());
        assertFalse(new DispatchRequest(1L, 2L, 2, null, LocalDateTime.now().minusHours(1), null).validate().isEmpty());
        assertTrue(new DispatchRequest(1L, 2L, 2, null, LocalDateTime.now().plusHours(1), null).validate().isEmpty());
    }
}

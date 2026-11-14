package com.civicair.briefing.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.civicair.briefing.analytics.AnalysisEntity;
import com.civicair.briefing.analytics.AnalyticsService;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class ComparisonServiceTest {
    @Test
    void comparesOnlyCompatibleAnalysesAndReturnsEvidenceForBothValues() {
        UUID owner = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        AnalysisEntity current = analysis(owner, 0.8);
        AnalysisEntity previous = analysis(owner, 1.0);
        UUID currentEvidence = UUID.randomUUID();
        UUID previousEvidence = UUID.randomUUID();
        current.addFinding(new FindingEntity("POLLUTANT_AVERAGE", "current", "PM10", 22.0, "ug/m3",
                "[\"" + currentEvidence + "\"]"));
        previous.addFinding(new FindingEntity("POLLUTANT_AVERAGE", "previous", "PM10", 20.0, "ug/m3",
                "[\"" + previousEvidence + "\"]"));
        AnalyticsService analytics = mock(AnalyticsService.class);
        when(analytics.requireAnalysis(currentId, owner)).thenReturn(current);
        when(analytics.requireAnalysis(previousId, owner)).thenReturn(previous);
        ComparisonService service = new ComparisonService(analytics, new ObjectMapper());

        var result = service.compare(new ComparisonService.ComparisonRequest(currentId, previousId), owner);
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.classification()).isEqualTo("WORSENED");
            assertThat(change.evidenceIds()).containsExactlyInAnyOrder(currentEvidence, previousEvidence);
        });
        assertThat(result.confidenceChange()).isCloseTo(-0.2, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void rejectsDifferentTimeWindows() {
        UUID owner = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        AnalysisEntity current = analysis(owner, 1.0);
        AnalysisEntity previous = new AnalysisEntity(owner, UUID.randomUUID(), "DESCRIPTIVE_V1", "Ward 1",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), OffsetDateTime.parse("2026-01-03T00:00:00Z"), 1.0);
        AnalyticsService analytics = mock(AnalyticsService.class);
        when(analytics.requireAnalysis(currentId, owner)).thenReturn(current);
        when(analytics.requireAnalysis(previousId, owner)).thenReturn(previous);
        ComparisonService service = new ComparisonService(analytics, new ObjectMapper());

        assertThatThrownBy(() -> service.compare(new ComparisonService.ComparisonRequest(currentId, previousId), owner))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("equally sized");
    }

    private AnalysisEntity analysis(UUID owner, double completeness) {
        return new AnalysisEntity(owner, UUID.randomUUID(), "DESCRIPTIVE_V1", "Ward 1",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), OffsetDateTime.parse("2026-01-02T00:00:00Z"),
                completeness);
    }
}

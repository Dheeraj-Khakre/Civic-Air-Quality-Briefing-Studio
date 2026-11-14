package com.civicair.briefing.briefing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.civicair.briefing.ai.BriefLanguageGenerator;
import com.civicair.briefing.analytics.AnalysisEntity;
import com.civicair.briefing.analytics.AnalyticsService;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.audit.AuditService;
import com.civicair.briefing.common.ApiException;
import com.civicair.briefing.common.RequestRateLimiter;
import com.civicair.briefing.briefing.BriefService.BriefRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class BriefEvidenceValidationTest {
    @Test
    void rejectsGeneratedEvidenceIdsThatDoNotBelongToAnalysis() {
        UUID owner = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID sourceEvidence = UUID.randomUUID();
        UUID fabricatedEvidence = UUID.randomUUID();
        AnalysisEntity analysis = new AnalysisEntity(owner, UUID.randomUUID(), "DESCRIPTIVE_V1", "Ward 1",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), OffsetDateTime.parse("2026-01-02T00:00:00Z"), 1.0);
        analysis.addFinding(new FindingEntity("POLLUTANT_AVERAGE", "Average PM2.5", "PM2.5",
                10.0, "ug/m3", "[\"" + sourceEvidence + "\"]"));

        AnalyticsService analytics = mock(AnalyticsService.class);
        when(analytics.requireAnalysis(analysisId, owner)).thenReturn(analysis);
        BriefRepository repository = mock(BriefRepository.class);
        BriefLanguageGenerator generator = mock(BriefLanguageGenerator.class);
        when(generator.generate(any(), any(), any())).thenReturn(
                new BriefLanguageGenerator.GeneratedBrief("Unsupported value 999.", List.of(fabricatedEvidence)));
        BriefService service = new BriefService(analytics, repository, new ObjectMapper(), generator,
                mock(AuditService.class), mock(RequestRateLimiter.class));

        assertThatThrownBy(() -> service.generate(analysisId, "INTERNAL",
                new BriefRequest("SCHOOL", "en", "SIMPLE"), owner))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("valid evidence");
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsOnlySupportedBriefOptions() {
        BriefService service = new BriefService(mock(AnalyticsService.class), mock(BriefRepository.class),
                new ObjectMapper(), mock(BriefLanguageGenerator.class), mock(AuditService.class),
                mock(RequestRateLimiter.class));
        assertThatThrownBy(() -> service.generate(UUID.randomUUID(), "INTERNAL",
                new BriefRequest("WARD_OFFICER", "fr", "SIMPLE"), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not supported");
    }
}

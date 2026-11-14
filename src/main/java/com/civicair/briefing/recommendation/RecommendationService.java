package com.civicair.briefing.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.civicair.briefing.analytics.AnalysisEntity;
import com.civicair.briefing.analytics.AnalyticsService;
import com.civicair.briefing.analytics.FindingEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RecommendationService {
    private final AnalyticsService analytics;
    private final ObjectMapper objectMapper;

    public RecommendationService(AnalyticsService analytics, ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> forAnalysis(UUID analysisId, Audience audience, UUID ownerId) {
        AnalysisEntity analysis = analytics.requireAnalysis(analysisId, ownerId);
        List<RecommendationResponse> responses = new ArrayList<>();
        for (FindingEntity finding : analysis.getFindings()) {
            if (!finding.getCategory().equals("POLLUTANT_AVERAGE")) continue;
            String action = switch (audience) {
                case WARD_OFFICER -> "Review the station and ward context for " + finding.getPollutant()
                        + " readings, and arrange follow-up monitoring before taking enforcement action.";
                case HOUSING_SOCIETY -> "Review local monitoring updates for " + finding.getPollutant()
                        + " and share verified updates with residents; this dataset alone does not establish indoor conditions.";
                case SCHOOL -> "Review local monitoring updates for " + finding.getPollutant()
                        + " with the relevant civic authority before changing school activities.";
            };
            responses.add(new RecommendationResponse(UUID.randomUUID(), action, "MEDIUM", audience,
                    "The submitted dataset contains " + finding.getPollutant() + " observations; the reported average is descriptive, not a threshold judgment.",
                    evidence(finding.getEvidenceIds()),
                    List.of("No configured AQI breakpoint or health-risk threshold was applied.",
                            "Confirm measurement coverage, sensor quality and local conditions before acting.")));
        }
        return responses;
    }

    private List<UUID> evidence(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }

    public enum Audience { WARD_OFFICER, HOUSING_SOCIETY, SCHOOL }
    public record RecommendationResponse(UUID id, String action, String priority, Audience audience,
            String reason, List<UUID> evidenceIds, List<String> limitations) {}
}

package com.civicair.briefing.analytics;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.civicair.briefing.briefing.BriefService;
import com.civicair.briefing.briefing.BriefService.BriefRequest;
import com.civicair.briefing.briefing.BriefService.BriefResponse;
import com.civicair.briefing.comparison.ComparisonService;
import com.civicair.briefing.comparison.ComparisonService.ComparisonRequest;
import com.civicair.briefing.comparison.ComparisonService.ComparisonResponse;
import com.civicair.briefing.recommendation.RecommendationService;
import com.civicair.briefing.recommendation.RecommendationService.Audience;
import com.civicair.briefing.recommendation.RecommendationService.RecommendationResponse;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {
    private final AnalyticsService analytics;
    private final RecommendationService recommendations;
    private final BriefService briefs;
    private final ComparisonService comparisons;

    public AnalysisController(AnalyticsService analytics, RecommendationService recommendations,
            BriefService briefs, ComparisonService comparisons) {
        this.analytics = analytics;
        this.recommendations = recommendations;
        this.briefs = briefs;
        this.comparisons = comparisons;
    }

    @PostMapping("/datasets/{datasetId}/analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyze(@PathVariable UUID datasetId, @Valid @RequestBody AnalysisRequest request,
            JwtAuthenticationToken principal) {
        return analytics.analyze(datasetId, request, ownerId(principal));
    }

    @GetMapping("/analyses/{analysisId}")
    public AnalysisResponse getAnalysis(@PathVariable UUID analysisId, JwtAuthenticationToken principal) {
        return analytics.get(analysisId, ownerId(principal));
    }

    @GetMapping("/analyses/{analysisId}/findings")
    public List<FindingResponse> findings(@PathVariable UUID analysisId, JwtAuthenticationToken principal) {
        return analytics.findings(analysisId, ownerId(principal));
    }

    @GetMapping("/analyses/{analysisId}/recommendations")
    @PreAuthorize("hasAuthority('ROLE_' + #audience.name())")
    public List<RecommendationResponse> recommendations(@PathVariable UUID analysisId,
            @RequestParam Audience audience, JwtAuthenticationToken principal) {
        return recommendations.forAnalysis(analysisId, audience, ownerId(principal));
    }

    @PostMapping("/analyses/{analysisId}/briefs")
    @PreAuthorize("hasAuthority('ROLE_' + #request.audience())")
    @ResponseStatus(HttpStatus.CREATED)
    public BriefResponse brief(@PathVariable UUID analysisId, @Valid @RequestBody BriefRequest request,
            JwtAuthenticationToken principal) {
        return briefs.generate(analysisId, "INTERNAL", request, ownerId(principal));
    }

    @PostMapping("/analyses/{analysisId}/citizen-advisories")
    @PreAuthorize("hasAuthority('ROLE_' + #request.audience())")
    @ResponseStatus(HttpStatus.CREATED)
    public BriefResponse advisory(@PathVariable UUID analysisId, @Valid @RequestBody BriefRequest request,
            JwtAuthenticationToken principal) {
        return briefs.generate(analysisId, "CITIZEN_ADVISORY", request, ownerId(principal));
    }

    @GetMapping("/briefs")
    public List<BriefResponse> history(JwtAuthenticationToken principal) {
        return briefs.history(ownerId(principal));
    }

    @GetMapping("/briefs/{briefId}")
    public BriefResponse getBrief(@PathVariable UUID briefId, JwtAuthenticationToken principal) {
        return briefs.get(briefId, ownerId(principal));
    }

    @PostMapping("/briefs/compare")
    public ComparisonResponse compare(@Valid @RequestBody ComparisonRequest request, JwtAuthenticationToken principal) {
        return comparisons.compare(request, ownerId(principal));
    }

    private UUID ownerId(JwtAuthenticationToken principal) {
        return UUID.fromString(principal.getToken().getClaimAsString("userId"));
    }

    public record AnalysisRequest(@NotBlank @Size(max = 32) String standard,
            @Size(max = 120) String ward) {}
    public record AnalysisResponse(UUID id, UUID datasetId, String status, String standard, OffsetDateTime createdAt,
            double dataCompleteness, List<PollutantMetric> metrics, List<LocationMetric> locations) {}
    public record PollutantMetric(String pollutant, double average, double minimum, double maximum,
            String unit, int observationCount, List<UUID> evidenceIds) {}
    public record LocationMetric(String location, String pollutant, double average, String unit, int observationCount) {}
    public record FindingResponse(UUID id, String category, String summary, String pollutant, Double value,
            String unit, List<UUID> evidenceIds, double confidence, List<String> limitations) {}
}

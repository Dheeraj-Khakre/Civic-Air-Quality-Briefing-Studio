package com.civicair.briefing.briefing;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.civicair.briefing.ai.BriefLanguageGenerator;
import com.civicair.briefing.audit.AuditService;
import com.civicair.briefing.analytics.AnalysisEntity;
import com.civicair.briefing.analytics.AnalyticsService;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.common.ApiException;
import com.civicair.briefing.common.RequestRateLimiter;
import com.civicair.briefing.recommendation.RecommendationService.Audience;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BriefService {
    private final AnalyticsService analytics;
    private final BriefRepository briefs;
    private final ObjectMapper objectMapper;
    private final BriefLanguageGenerator languageGenerator;
    private final AuditService audit;
    private final RequestRateLimiter rateLimiter;

    public BriefService(AnalyticsService analytics, BriefRepository briefs, ObjectMapper objectMapper,
            BriefLanguageGenerator languageGenerator, AuditService audit, RequestRateLimiter rateLimiter) {
        this.analytics = analytics;
        this.briefs = briefs;
        this.objectMapper = objectMapper;
        this.languageGenerator = languageGenerator;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public BriefResponse generate(UUID analysisId, String type, BriefRequest request, UUID ownerId) {
        validateRequest(request);
        rateLimiter.check(ownerId, "brief-generation", 20, java.time.Duration.ofHours(1));
        AnalysisEntity analysis = analytics.requireAnalysis(analysisId, ownerId);
        List<FindingEntity> evidence = analysis.getFindings().stream()
                .filter(finding -> finding.getCategory().equals("POLLUTANT_AVERAGE")).toList();
        if (evidence.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_EVIDENCE",
                "More validated observations are needed before a brief can be generated.");
        List<UUID> permittedEvidence = evidence.stream().map(FindingEntity::getId).distinct().toList();
        var generated = languageGenerator.generate(type, request, evidence);
        if (generated == null || generated.content() == null || generated.content().isBlank()
                || generated.evidenceIds() == null || generated.evidenceIds().isEmpty()
                || !permittedEvidence.containsAll(generated.evidenceIds())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_OUTPUT_REJECTED",
                    "Brief output did not reference valid evidence from this analysis.");
        }
        BriefEntity brief = briefs.save(new BriefEntity(ownerId, analysisId, type, request.audience(),
                request.language(), request.readingLevel(), generated.content(), writeEvidence(generated.evidenceIds())));
        audit.record(ownerId, "BRIEF_GENERATED", brief.getId());
        return response(brief);
    }

    private void validateRequest(BriefRequest request) {
        if (!List.of("WARD_OFFICER", "HOUSING_SOCIETY", "SCHOOL").contains(request.audience())
                || !List.of("en", "hi").contains(request.language())
                || !List.of("SIMPLE", "DETAILED").contains(request.readingLevel())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BRIEF_OPTIONS",
                    "Audience, language or reading level is not supported.");
        }
    }

    @Transactional(readOnly = true)
    public List<BriefResponse> history(UUID ownerId) {
        return briefs.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public BriefResponse get(UUID id, UUID ownerId) {
        return briefs.findByIdAndOwnerId(id, ownerId).map(this::response)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BRIEF_NOT_FOUND", "Brief was not found."));
    }

    private List<UUID> readEvidence(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }
    private String writeEvidence(List<UUID> ids) {
        try { return objectMapper.writeValueAsString(ids); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not store evidence references.", exception); }
    }
    private BriefResponse response(BriefEntity brief) {
        return new BriefResponse(brief.getId(), brief.getAnalysisId(), brief.getType(), brief.getAudience(),
                brief.getLanguage(), brief.getReadingLevel(), brief.getContent(), readEvidence(brief.getEvidenceIds()),
                List.of("Descriptive statistics only; no AQI breakpoint or health-risk threshold was applied."),
                brief.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    public record BriefRequest(@NotBlank String audience, @NotBlank String language,
            @NotBlank String readingLevel) {}
    public record BriefResponse(UUID id, UUID analysisId, String type, String audience, String language,
            String readingLevel, String content, List<UUID> evidenceIds, List<String> limitations,
            java.time.OffsetDateTime createdAt) {}
}

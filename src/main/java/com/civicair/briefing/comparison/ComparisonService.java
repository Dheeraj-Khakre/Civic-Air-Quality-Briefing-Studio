package com.civicair.briefing.comparison;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.civicair.briefing.analytics.AnalysisEntity;
import com.civicair.briefing.analytics.AnalyticsService;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ComparisonService {
    private final AnalyticsService analytics;
    private final ObjectMapper objectMapper;

    public ComparisonService(AnalyticsService analytics, ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ComparisonResponse compare(ComparisonRequest request, UUID ownerId) {
        AnalysisEntity current = analytics.requireAnalysis(request.currentAnalysisId(), ownerId);
        AnalysisEntity previous = analytics.requireAnalysis(request.previousAnalysisId(), ownerId);
        if (!current.getStandard().equals(previous.getStandard())
                || !current.getLocationName().equalsIgnoreCase(previous.getLocationName())
                || current.getPeriodStart() == null || current.getPeriodEnd() == null
                || previous.getPeriodStart() == null || previous.getPeriodEnd() == null
                || !Duration.between(current.getPeriodStart(), current.getPeriodEnd())
                        .equals(Duration.between(previous.getPeriodStart(), previous.getPeriodEnd()))) {
            throw new ApiException(HttpStatus.CONFLICT, "INCOMPATIBLE_ANALYSES",
                    "Analyses must use the same standard and location and have equally sized declared time windows.");
        }
        Map<String, FindingEntity> currentFindings = averages(current);
        Map<String, FindingEntity> previousFindings = averages(previous);
        List<ComparisonChange> changes = new ArrayList<>();
        java.util.Set<String> pollutants = new java.util.TreeSet<>(currentFindings.keySet());
        pollutants.addAll(previousFindings.keySet());
        for (String pollutant : pollutants) {
            FindingEntity currentMetric = currentFindings.get(pollutant);
            FindingEntity previousMetric = previousFindings.get(pollutant);
            if (currentMetric == null || previousMetric == null) {
                changes.add(new ComparisonChange(pollutant, "MISSING_DATA",
                        currentMetric == null ? null : currentMetric.getValue(),
                        previousMetric == null ? null : previousMetric.getValue(), "ug/m3",
                        evidenceIds(currentMetric, previousMetric)));
            } else {
                String classification = Double.compare(currentMetric.getValue(), previousMetric.getValue()) == 0
                        ? "UNCHANGED" : currentMetric.getValue() < previousMetric.getValue() ? "IMPROVED" : "WORSENED";
                changes.add(new ComparisonChange(pollutant, classification, currentMetric.getValue(),
                        previousMetric.getValue(), "ug/m3", evidenceIds(currentMetric, previousMetric)));
            }
        }
        double confidenceChange = current.getCompleteness() - previous.getCompleteness();
        return new ComparisonResponse(current.getId(), previous.getId(), changes, confidenceChange,
                List.of("Comparison is descriptive and uses equal-duration declared periods; it does not establish causation.",
                        "Confidence change is the change in valid-row completeness, not statistical confidence.",
                        "A pollutant present in only one analysis is reported as MISSING_DATA, not as a newly detected health issue."));
    }

    private Map<String, FindingEntity> averages(AnalysisEntity analysis) {
        Map<String, FindingEntity> result = new HashMap<>();
        analysis.getFindings().stream().filter(finding -> finding.getCategory().equals("POLLUTANT_AVERAGE"))
                .forEach(finding -> result.put(finding.getPollutant(), finding));
        return result;
    }
    private List<UUID> evidenceIds(FindingEntity first, FindingEntity second) {
        List<UUID> ids = new ArrayList<>();
        if (first != null) ids.addAll(read(first.getEvidenceIds()));
        if (second != null) ids.addAll(read(second.getEvidenceIds()));
        return ids.stream().distinct().toList();
    }
    private List<UUID> read(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }

    public record ComparisonRequest(@NotNull UUID currentAnalysisId, @NotNull UUID previousAnalysisId) {}
    public record ComparisonChange(String pollutant, String classification, Double currentValue,
            Double previousValue, String unit, List<UUID> evidenceIds) {}
    public record ComparisonResponse(UUID currentAnalysisId, UUID previousAnalysisId, List<ComparisonChange> changes,
            double confidenceChange, List<String> limitations) {}
}

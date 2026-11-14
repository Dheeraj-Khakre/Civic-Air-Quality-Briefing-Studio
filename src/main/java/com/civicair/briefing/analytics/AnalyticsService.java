package com.civicair.briefing.analytics;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.civicair.briefing.analytics.AnalysisController.AnalysisRequest;
import com.civicair.briefing.analytics.AnalysisController.AnalysisResponse;
import com.civicair.briefing.analytics.AnalysisController.FindingResponse;
import com.civicair.briefing.analytics.AnalysisController.LocationMetric;
import com.civicair.briefing.analytics.AnalysisController.PollutantMetric;
import com.civicair.briefing.common.ApiException;
import com.civicair.briefing.audit.AuditService;
import com.civicair.briefing.ingestion.DatasetEntity;
import com.civicair.briefing.ingestion.DatasetRepository;
import com.civicair.briefing.ingestion.ObservationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AnalyticsService {
    public static final String DESCRIPTIVE_STANDARD = "DESCRIPTIVE_V1";
    private final DatasetRepository datasets;
    private final AnalysisRepository analyses;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public AnalyticsService(DatasetRepository datasets, AnalysisRepository analyses, ObjectMapper objectMapper,
            AuditService audit) {
        this.datasets = datasets;
        this.analyses = analyses;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public AnalysisResponse analyze(UUID datasetId, AnalysisRequest request, UUID ownerId) {
        if (!DESCRIPTIVE_STANDARD.equals(request.standard())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_STANDARD",
                    "Only DESCRIPTIVE_V1 is configured. AQI analysis requires a documented configured breakpoint standard.");
        }
        DatasetEntity dataset = datasets.findByIdAndOwnerId(datasetId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DATASET_NOT_FOUND", "Dataset was not found."));
        List<ObservationEntity> rows = dataset.getObservations();
        List<ObservationEntity> valid = rows.stream().filter(row -> row.getNormalizedValue() != null
                && row.getPollutant() != null && !row.isDuplicate()).toList();
        if (valid.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_VALID_MEASUREMENTS",
                "Dataset has no valid normalized measurements to analyze.");
        double completeness = rows.isEmpty() ? 0 : (double) valid.size() / rows.size();
        AnalysisEntity analysis = new AnalysisEntity(ownerId, datasetId, request.standard(), dataset.getLocationName(),
                dataset.getPeriodStart(), dataset.getPeriodEnd(), completeness);

        Map<String, List<ObservationEntity>> byPollutant = valid.stream()
                .collect(Collectors.groupingBy(ObservationEntity::getPollutant, LinkedHashMap::new, Collectors.toList()));
        for (var entry : byPollutant.entrySet()) {
            List<ObservationEntity> pollutantRows = entry.getValue();
            double average = pollutantRows.stream().mapToDouble(ObservationEntity::getNormalizedValue).average().orElseThrow();
            double minimum = pollutantRows.stream().mapToDouble(ObservationEntity::getNormalizedValue).min().orElseThrow();
            double maximum = pollutantRows.stream().mapToDouble(ObservationEntity::getNormalizedValue).max().orElseThrow();
            List<UUID> evidence = pollutantRows.stream().map(ObservationEntity::getId).toList();
            analysis.addFinding(new FindingEntity("POLLUTANT_AVERAGE",
                    "Observed " + entry.getKey() + " average: " + format(average) + " ug/m3 across " + pollutantRows.size() + " valid readings.",
                    entry.getKey(), average, "ug/m3", serialize(evidence)));

            var locationGroups = pollutantRows.stream().collect(Collectors.groupingBy(ObservationEntity::getLocationName));
            locationGroups.entrySet().stream()
                    .map(group -> new LocationAverage(group.getKey(),
                            group.getValue().stream().mapToDouble(ObservationEntity::getNormalizedValue).average().orElseThrow(),
                            group.getValue()))
                    .max(Comparator.comparingDouble(LocationAverage::average))
                    .ifPresent(hotspot -> {
                        if (locationGroups.size() > 1) {
                            List<UUID> hotspotEvidence = hotspot.rows().stream().map(ObservationEntity::getId).toList();
                            analysis.addFinding(new FindingEntity("POTENTIAL_HOTSPOT",
                                    "Highest observed " + entry.getKey() + " average in the submitted locations: "
                                            + hotspot.location() + " (" + format(hotspot.average()) + " ug/m3).",
                                    entry.getKey(), hotspot.average(), "ug/m3", serialize(hotspotEvidence)));
                        }
                    });
        }
        analyses.save(analysis);
        audit.record(ownerId, "ANALYSIS_CREATED", analysis.getId());
        return response(analysis, valid);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(UUID id, UUID ownerId) {
        AnalysisEntity analysis = requireAnalysis(id, ownerId);
        return response(analysis, validRows(analysis));
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> findings(UUID id, UUID ownerId) {
        AnalysisEntity analysis = requireAnalysis(id, ownerId);
        return analysis.getFindings().stream().map(finding -> new FindingResponse(finding.getId(),
                finding.getCategory(), finding.getSummary(), finding.getPollutant(), finding.getValue(),
                finding.getUnit(), deserialize(finding.getEvidenceIds()), analysis.getCompleteness(),
                List.of("Confidence is represented by the fraction of dataset rows that produced valid measurements; it is not statistical uncertainty.",
                        "Descriptive statistics only; values are not an AQI or a health-risk determination."))).toList();
    }

    public AnalysisEntity requireAnalysis(UUID id, UUID ownerId) {
        return analyses.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analysis was not found."));
    }

    List<UUID> evidenceIds(AnalysisEntity analysis) {
        return analysis.getFindings().stream().flatMap(finding -> deserialize(finding.getEvidenceIds()).stream()).distinct().toList();
    }

    private List<ObservationEntity> validRows(AnalysisEntity analysis) {
        DatasetEntity dataset = datasets.findByIdAndOwnerId(analysis.getDatasetId(), analysis.getOwnerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DATASET_NOT_FOUND", "Dataset was not found."));
        return dataset.getObservations().stream().filter(row -> row.getNormalizedValue() != null
                && row.getPollutant() != null && !row.isDuplicate()).toList();
    }

    private AnalysisResponse response(AnalysisEntity analysis, List<ObservationEntity> valid) {
        List<PollutantMetric> metrics = analysis.getFindings().stream()
                .filter(finding -> finding.getCategory().equals("POLLUTANT_AVERAGE"))
                .map(finding -> {
                    List<ObservationEntity> pollutantRows = valid.stream()
                            .filter(row -> row.getPollutant().equals(finding.getPollutant())).toList();
                    double minimum = pollutantRows.stream().mapToDouble(ObservationEntity::getNormalizedValue).min().orElse(finding.getValue());
                    double maximum = pollutantRows.stream().mapToDouble(ObservationEntity::getNormalizedValue).max().orElse(finding.getValue());
                    return new PollutantMetric(finding.getPollutant(), finding.getValue(), minimum, maximum, finding.getUnit(),
                            pollutantRows.size(), deserialize(finding.getEvidenceIds()));
                }).toList();
        List<LocationMetric> locations = valid.stream().collect(Collectors.groupingBy(row -> row.getLocationName() + "|" + row.getPollutant()))
                .values().stream().map(group -> new LocationMetric(group.getFirst().getLocationName(),
                        group.getFirst().getPollutant(),
                        group.stream().mapToDouble(ObservationEntity::getNormalizedValue).average().orElseThrow(),
                        "ug/m3", group.size())).toList();
        return new AnalysisResponse(analysis.getId(), analysis.getDatasetId(), analysis.getStatus(),
                analysis.getStandard(), analysis.getCreatedAt().atOffset(ZoneOffset.UTC), analysis.getCompleteness(), metrics, locations);
    }

    private String serialize(List<UUID> ids) {
        try { return objectMapper.writeValueAsString(ids); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not store evidence references.", exception); }
    }
    private List<UUID> deserialize(String ids) {
        try { return objectMapper.readValue(ids, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }
    private String format(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
    private record LocationAverage(String location, double average, List<ObservationEntity> rows) {}
}

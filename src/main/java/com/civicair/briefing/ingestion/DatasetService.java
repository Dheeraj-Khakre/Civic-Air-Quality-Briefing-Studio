package com.civicair.briefing.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.civicair.briefing.common.ApiException;
import com.civicair.briefing.audit.AuditService;
import com.civicair.briefing.ingestion.DatasetController.DatasetResponse;
import com.civicair.briefing.ingestion.DatasetController.NormalizationDecision;
import com.civicair.briefing.ingestion.DatasetController.PastedDatasetRequest;
import com.civicair.briefing.ingestion.DatasetController.QualityIssue;
import com.civicair.briefing.ingestion.DatasetController.QualityReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.civicair.briefing.normalization.ObservationNormalizer;
import com.civicair.briefing.validation.DatasetQualityValidator;

@Service
public class DatasetService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private final DatasetRepository datasets;
    private final QualityIssueRepository issues;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final ObservationNormalizer normalizer;
    private final DatasetQualityValidator qualityValidator;
    private final TabularDataParser parser;

    public DatasetService(DatasetRepository datasets, QualityIssueRepository issues, ObjectMapper objectMapper,
            AuditService audit, ObservationNormalizer normalizer, DatasetQualityValidator qualityValidator,
            TabularDataParser parser) {
        this.datasets = datasets;
        this.issues = issues;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.normalizer = normalizer;
        this.qualityValidator = qualityValidator;
        this.parser = parser;
    }

    @Transactional
    public DatasetResponse upload(MultipartFile file, String location, OffsetDateTime periodStart,
            OffsetDateTime periodEnd, UUID ownerId) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "Upload must contain data and be at most 20 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            TabularDataParser.ParsedFile parsed = parser.parseUpload(bytes);
            String name = safeFilename(file.getOriginalFilename());
            return persist(name, parsed.type(), parsed.rows(), location, periodStart, periodEnd,
                    sha256(bytes), ownerId);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNREADABLE_UPLOAD",
                    "Upload is not a readable CSV or XLSX file.");
        }
    }

    @Transactional
    public DatasetResponse pasted(PastedDatasetRequest request, UUID ownerId) {
        if (!List.of(",", ";", "\t").contains(request.delimiter())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DELIMITER", "Delimiter must be comma, semicolon or tab.");
        }
        TabularDataParser.ParsedFile parsed = parser.parsePasted(request.content(), request.delimiter().charAt(0));
        return persist(normalizer.cleanText(request.name(), 160), "PASTED", parsed.rows(), request.locationName(),
                request.periodStart(), request.periodEnd(), sha256(request.content().getBytes(StandardCharsets.UTF_8)), ownerId);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponse> list(UUID ownerId) {
        return datasets.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID id, UUID ownerId) {
        return response(requireDataset(id, ownerId));
    }

    @Transactional(readOnly = true)
    public QualityReport quality(UUID id, UUID ownerId) {
        DatasetEntity dataset = requireDataset(id, ownerId);
        List<QualityIssueEntity> found = issues.findAllByDatasetIdOrderByRowNumberAsc(id);
        List<NormalizationDecision> decisions = dataset.getObservations().stream()
                .filter(row -> row.getOriginalUnit() != null || row.getPollutant() != null)
                .map(row -> new NormalizationDecision(row.getRowNumber(), "measurement",
                        valueString(row.getOriginalValue(), row.getOriginalUnit()),
                        valueString(row.getNormalizedValue(), row.getNormalizedUnit()),
                        row.getNormalizedValue() == null ? "PRESERVED_UNNORMALIZED" : "CANONICAL_UNIT"))
                .toList();
        return new QualityReport(id, "COMPLETE", dataset.getObservations().size(), found.size(),
                found.stream().map(issue -> new QualityIssue(issue.getId(), issue.getRowNumber(),
                        issue.getCode(), issue.getSeverity(), issue.getMessage())).toList(), decisions);
    }

    DatasetEntity requireDataset(UUID id, UUID ownerId) {
        return datasets.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DATASET_NOT_FOUND", "Dataset was not found."));
    }

    private DatasetResponse persist(String name, String sourceType, List<Map<String, String>> rows, String location,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, String digest, UUID ownerId) {
        if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_DATASET", "Dataset contains no data rows.");
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "periodEnd must be after periodStart.");
        }
        String safeLocation = normalizer.cleanText(location, 160);
        DatasetEntity dataset = new DatasetEntity(ownerId, name, sourceType, safeLocation, periodStart, periodEnd, digest);
        List<QualityIssueEntity> foundIssues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> timestampFormats = new HashSet<>();
        int rowNumber = 1;
        for (Map<String, String> raw : rows) {
            rowNumber++;
            Map<String, String> fields = normalizer.canonicalFields(raw);
            String rawPollutant = fields.get("pollutant");
            List<Map.Entry<String, String>> readings = new ArrayList<>();
            if (!blank(rawPollutant)) {
                readings.add(Map.entry(rawPollutant == null ? "" : rawPollutant, fields.getOrDefault("value", "")));
            } else {
                raw.forEach((header, value) -> {
                    String widePollutant = normalizer.canonicalPollutant(header);
                    if (widePollutant != null) {
                        readings.add(Map.entry(widePollutant, value == null ? "" : value));
                    }
                });
            }
            if (readings.isEmpty()) {
                foundIssues.add(issue(dataset, rowNumber, "MISSING_POLLUTANT", "ERROR", "No recognized pollutant measurement was found."));
                OffsetDateTime timestamp = normalizer.parseTimestamp(fields.get("timestamp"));
                dataset.addObservation(new ObservationEntity(rowNumber, serialize(raw), null, timestamp,
                        firstNonBlank(fields.get("location"), safeLocation), normalizer.parseNumber(fields.get("value")),
                        fields.get("unit"), null, null, false));
                continue;
            }
            for (Map.Entry<String, String> reading : readings) {
                String actualPollutant = normalizer.canonicalPollutant(reading.getKey());
                if (actualPollutant == null) {
                    foundIssues.add(issue(dataset, rowNumber, blank(reading.getKey()) ? "MISSING_POLLUTANT" : "UNKNOWN_POLLUTANT",
                            "ERROR", blank(reading.getKey()) ? "Pollutant is missing." : "Pollutant is not supported; original value was retained."));
                }
                String timeValue = fields.get("timestamp");
                OffsetDateTime timestamp = normalizer.parseTimestamp(timeValue);
                if (timestamp != null) timestampFormats.add(normalizer.timestampFormat(timeValue));
                if (timestamp == null) foundIssues.add(issue(dataset, rowNumber, blank(timeValue) ? "MISSING_TIMESTAMP" : "INVALID_TIMESTAMP",
                        "WARNING", blank(timeValue) ? "Observation timestamp is missing." : "Timestamp could not be parsed."));
                String rowLocation = firstNonBlank(fields.get("location"), safeLocation);
                Double value = normalizer.parseNumber(reading.getValue());
                if (value == null) foundIssues.add(issue(dataset, rowNumber,
                        blank(reading.getValue()) ? "MISSING_VALUE" : "INVALID_MEASUREMENT", "ERROR",
                        blank(reading.getValue()) ? "Measurement value is missing." : "Measurement is not a valid number."));
                else if (value < 0) {
                    foundIssues.add(issue(dataset, rowNumber, "INVALID_MEASUREMENT", "ERROR", "Negative measurements are invalid."));
                    value = null;
                }
                String unit = fields.get("unit");
                ObservationNormalizer.NormalizedMeasurement normalized = normalizer.normalizeUnit(actualPollutant, unit, value);
                if (value != null && !blank(unit) && !normalized.supported()) {
                    foundIssues.add(issue(dataset, rowNumber, "UNSUPPORTED_UNIT", "ERROR",
                            "Unit is unsupported for this pollutant; value was not converted."));
                } else if (value != null && blank(unit)) {
                    foundIssues.add(issue(dataset, rowNumber, "MISSING_UNIT", "ERROR", "Measurement unit is missing."));
                }
                boolean duplicate = false;
                if (timestamp != null && actualPollutant != null) {
                    duplicate = !seen.add(timestamp + "|" + rowLocation.toLowerCase(Locale.ROOT) + "|" + actualPollutant);
                    if (duplicate) foundIssues.add(issue(dataset, rowNumber, "DUPLICATE_RECORD", "WARNING",
                            "A measurement with the same timestamp, location and pollutant was already present."));
                }
                dataset.addObservation(new ObservationEntity(rowNumber, serialize(raw), actualPollutant,
                        timestamp, rowLocation, value, unit, normalized.value(), normalized.unit(), duplicate));
            }
        }
        if (timestampFormats.size() > 1) {
            foundIssues.add(issue(dataset, null, "INCONSISTENT_TIMESTAMP_FORMAT", "WARNING",
                    "Dataset uses more than one recognized timestamp format."));
        }
        foundIssues.addAll(qualityValidator.detectOutliers(dataset));
        datasets.save(dataset);
        issues.saveAll(foundIssues);
        audit.record(ownerId, "DATASET_INGESTED", dataset.getId());
        return response(dataset);
    }

    private String safeFilename(String filename) {
        String basename = filename == null ? "dataset.csv" : filename.replace('\\', '/').substring(filename.replace('\\', '/').lastIndexOf('/') + 1);
        String cleaned = normalizer.cleanText(basename, 160);
        return cleaned.isBlank() ? "dataset.csv" : cleaned;
    }
    private String serialize(Map<String, String> row) {
        try {
            String json = objectMapper.writeValueAsString(row);
            if (json.length() > 20_000) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ROW_TOO_LARGE",
                        "A source row exceeds the supported size limit.");
            }
            return json;
        }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not preserve source row.", exception); }
    }
    private String sha256(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private QualityIssueEntity issue(DatasetEntity dataset, Integer row, String code, String severity, String message) {
        return new QualityIssueEntity(dataset.getId(), row, code, severity, message);
    }
    private DatasetResponse response(DatasetEntity dataset) {
        return new DatasetResponse(dataset.getId(), dataset.getName(), dataset.getSourceType(), dataset.getStatus(),
                dataset.getLocationName(), dataset.getPeriodStart(), dataset.getPeriodEnd(),
                dataset.getObservations().size(), dataset.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
    private String valueString(Double value, String unit) {
        return value == null ? null : value + (unit == null ? "" : " " + unit);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String firstNonBlank(String preferred, String fallback) {
        return blank(preferred) ? fallback : normalizer.cleanText(preferred, 160);
    }
}

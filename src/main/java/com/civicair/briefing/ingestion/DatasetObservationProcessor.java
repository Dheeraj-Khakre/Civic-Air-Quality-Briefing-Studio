package com.civicair.briefing.ingestion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.civicair.briefing.common.ApiException;
import com.civicair.briefing.normalization.ObservationNormalizer;
import com.civicair.briefing.validation.DatasetQualityValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DatasetObservationProcessor {
    private final ObservationNormalizer normalizer;
    private final DatasetQualityValidator qualityValidator;
    private final ObjectMapper objectMapper;

    public DatasetObservationProcessor(ObservationNormalizer normalizer, DatasetQualityValidator qualityValidator,
            ObjectMapper objectMapper) {
        this.normalizer = normalizer;
        this.qualityValidator = qualityValidator;
        this.objectMapper = objectMapper;
    }

    public List<QualityIssueEntity> process(DatasetEntity dataset, List<Map<String, String>> rows,
            String defaultLocation) {
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
                readings.add(Map.entry(rawPollutant, fields.getOrDefault("value", "")));
            } else {
                raw.forEach((header, value) -> {
                    String widePollutant = normalizer.canonicalPollutant(header);
                    if (widePollutant != null) {
                        readings.add(Map.entry(widePollutant, value == null ? "" : value));
                    }
                });
            }
            if (readings.isEmpty()) {
                foundIssues.add(issue(dataset, rowNumber, "MISSING_POLLUTANT", "ERROR",
                        "No recognized pollutant measurement was found."));
                OffsetDateTime timestamp = normalizer.parseTimestamp(fields.get("timestamp"));
                dataset.addObservation(new ObservationEntity(rowNumber, serialize(raw), null, timestamp,
                        firstNonBlank(fields.get("location"), defaultLocation), normalizer.parseNumber(fields.get("value")),
                        fields.get("unit"), null, null, false));
                continue;
            }
            for (Map.Entry<String, String> reading : readings) {
                String actualPollutant = normalizer.canonicalPollutant(reading.getKey());
                if (actualPollutant == null) {
                    foundIssues.add(issue(dataset, rowNumber,
                            blank(reading.getKey()) ? "MISSING_POLLUTANT" : "UNKNOWN_POLLUTANT", "ERROR",
                            blank(reading.getKey()) ? "Pollutant is missing."
                                    : "Pollutant is not supported; original value was retained."));
                }
                String timeValue = fields.get("timestamp");
                OffsetDateTime timestamp = normalizer.parseTimestamp(timeValue);
                if (timestamp != null) timestampFormats.add(normalizer.timestampFormat(timeValue));
                if (timestamp == null) {
                    foundIssues.add(issue(dataset, rowNumber, blank(timeValue) ? "MISSING_TIMESTAMP" : "INVALID_TIMESTAMP",
                            "WARNING", blank(timeValue) ? "Observation timestamp is missing."
                                    : "Timestamp could not be parsed."));
                }
                String rowLocation = firstNonBlank(fields.get("location"), defaultLocation);
                Double value = normalizer.parseNumber(reading.getValue());
                if (value == null) {
                    foundIssues.add(issue(dataset, rowNumber,
                            blank(reading.getValue()) ? "MISSING_VALUE" : "INVALID_MEASUREMENT", "ERROR",
                            blank(reading.getValue()) ? "Measurement value is missing."
                                    : "Measurement is not a valid number."));
                } else if (value < 0) {
                    foundIssues.add(issue(dataset, rowNumber, "INVALID_MEASUREMENT", "ERROR",
                            "Negative measurements are invalid."));
                    value = null;
                }
                String unit = fields.get("unit");
                ObservationNormalizer.NormalizedMeasurement normalized =
                        normalizer.normalizeUnit(actualPollutant, unit, value);
                if (value != null && !blank(unit) && !normalized.supported()) {
                    foundIssues.add(issue(dataset, rowNumber, "UNSUPPORTED_UNIT", "ERROR",
                            "Unit is unsupported for this pollutant; value was not converted."));
                } else if (value != null && blank(unit)) {
                    foundIssues.add(issue(dataset, rowNumber, "MISSING_UNIT", "ERROR",
                            "Measurement unit is missing."));
                }
                boolean duplicate = false;
                if (timestamp != null && actualPollutant != null) {
                    duplicate = !seen.add(timestamp + "|" + rowLocation.toLowerCase(Locale.ROOT) + "|"
                            + actualPollutant);
                    if (duplicate) {
                        foundIssues.add(issue(dataset, rowNumber, "DUPLICATE_RECORD", "WARNING",
                                "A measurement with the same timestamp, location and pollutant was already present."));
                    }
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
        return foundIssues;
    }

    private String serialize(Map<String, String> row) {
        try {
            String json = objectMapper.writeValueAsString(row);
            if (json.length() > 20_000) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ROW_TOO_LARGE",
                        "A source row exceeds the supported size limit.");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not preserve source row.", exception);
        }
    }

    private QualityIssueEntity issue(DatasetEntity dataset, Integer row, String code, String severity,
            String message) {
        return new QualityIssueEntity(dataset.getId(), row, code, severity, message);
    }

    private String firstNonBlank(String preferred, String fallback) {
        return blank(preferred) ? fallback : normalizer.cleanText(preferred, 160);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

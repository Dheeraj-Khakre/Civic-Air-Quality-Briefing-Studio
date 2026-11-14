package com.civicair.briefing.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
import com.civicair.briefing.normalization.ObservationNormalizer;

@Service
public class DatasetService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private final DatasetRepository datasets;
    private final QualityIssueRepository issues;
    private final AuditService audit;
    private final ObservationNormalizer normalizer;
    private final TabularDataParser parser;
    private final DatasetObservationProcessor observationProcessor;

    public DatasetService(DatasetRepository datasets, QualityIssueRepository issues, AuditService audit,
            ObservationNormalizer normalizer, TabularDataParser parser,
            DatasetObservationProcessor observationProcessor) {
        this.datasets = datasets;
        this.issues = issues;
        this.audit = audit;
        this.normalizer = normalizer;
        this.parser = parser;
        this.observationProcessor = observationProcessor;
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
        List<QualityIssueEntity> foundIssues = observationProcessor.process(dataset, rows, safeLocation);
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
    private String sha256(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private DatasetResponse response(DatasetEntity dataset) {
        return new DatasetResponse(dataset.getId(), dataset.getName(), dataset.getSourceType(), dataset.getStatus(),
                dataset.getLocationName(), dataset.getPeriodStart(), dataset.getPeriodEnd(),
                dataset.getObservations().size(), dataset.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
    private String valueString(Double value, String unit) {
        return value == null ? null : value + (unit == null ? "" : " " + unit);
    }
}

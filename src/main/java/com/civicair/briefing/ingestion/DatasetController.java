package com.civicair.briefing.ingestion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.civicair.briefing.common.RequestRateLimiter;

@RestController
@Validated
@RequestMapping("/api/v1/datasets")
public class DatasetController {
    private final DatasetService datasets;
    private final RequestRateLimiter rateLimiter;

    public DatasetController(DatasetService datasets, RequestRateLimiter rateLimiter) {
        this.datasets = datasets;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(consumes = "multipart/form-data")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public DatasetResponse upload(@RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank @Size(max = 160) String locationName,
            @RequestParam(required = false) OffsetDateTime periodStart,
            @RequestParam(required = false) OffsetDateTime periodEnd,
            JwtAuthenticationToken principal) {
        UUID ownerId = ownerId(principal);
        rateLimiter.check(ownerId, "dataset-upload", 30, Duration.ofHours(1));
        return datasets.upload(file, locationName, periodStart, periodEnd, ownerId);
    }

    @PostMapping(path = "/pasted", consumes = "application/json")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public DatasetResponse pasted(@Valid @RequestBody PastedDatasetRequest request, JwtAuthenticationToken principal) {
        UUID ownerId = ownerId(principal);
        rateLimiter.check(ownerId, "dataset-upload", 30, Duration.ofHours(1));
        return datasets.pasted(request, ownerId);
    }

    @GetMapping
    public List<DatasetResponse> list(JwtAuthenticationToken principal) {
        return datasets.list(ownerId(principal));
    }

    @GetMapping("/{datasetId}")
    public DatasetResponse get(@PathVariable UUID datasetId, JwtAuthenticationToken principal) {
        return datasets.get(datasetId, ownerId(principal));
    }

    @GetMapping("/{datasetId}/quality-report")
    public QualityReport quality(@PathVariable UUID datasetId, JwtAuthenticationToken principal) {
        return datasets.quality(datasetId, ownerId(principal));
    }

    private UUID ownerId(JwtAuthenticationToken principal) {
        return UUID.fromString(principal.getToken().getClaimAsString("userId"));
    }

    public record PastedDatasetRequest(@NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 160) String locationName,
            OffsetDateTime periodStart, OffsetDateTime periodEnd,
            @NotBlank @Size(min = 1, max = 1) String delimiter,
            @NotBlank @Size(max = 2_000_000) String content) {}

    public record DatasetResponse(UUID id, String name, String sourceType, String status, String locationName,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, int rowCount, OffsetDateTime createdAt) {}
    public record QualityIssue(UUID id, Integer rowNumber, String code, String severity, String message) {}
    public record QualityReport(UUID datasetId, String status, int rowCount, int issueCount,
            List<QualityIssue> issues, List<NormalizationDecision> normalizationDecisions) {}
    public record NormalizationDecision(int rowNumber, String field, String originalValue,
            String normalizedValue, String decision) {}
}

package com.civicair.briefing.analytics;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis")
public class AnalysisEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID ownerId;
    @Column(nullable = false)
    private UUID datasetId;
    @Column(nullable = false, length = 32)
    private String standard;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false, length = 160)
    private String locationName;
    private OffsetDateTime periodStart;
    private OffsetDateTime periodEnd;
    @Column(nullable = false)
    private double completeness;
    @Column(nullable = false)
    private Instant createdAt;
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FindingEntity> findings = new ArrayList<>();

    protected AnalysisEntity() {}

    public AnalysisEntity(UUID ownerId, UUID datasetId, String standard, String locationName,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, double completeness) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.datasetId = datasetId;
        this.standard = standard;
        this.locationName = locationName;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.completeness = completeness;
        this.status = "COMPLETE";
        this.createdAt = Instant.now();
    }

    public void addFinding(FindingEntity finding) {
        findings.add(finding);
        finding.setAnalysis(this);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getDatasetId() { return datasetId; }
    public String getStandard() { return standard; }
    public String getStatus() { return status; }
    public String getLocationName() { return locationName; }
    public OffsetDateTime getPeriodStart() { return periodStart; }
    public OffsetDateTime getPeriodEnd() { return periodEnd; }
    public double getCompleteness() { return completeness; }
    public Instant getCreatedAt() { return createdAt; }
    public List<FindingEntity> getFindings() { return findings; }
}

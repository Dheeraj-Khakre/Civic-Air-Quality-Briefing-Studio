package com.civicair.briefing.ingestion;

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
@Table(name = "dataset")
public class DatasetEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID ownerId;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 16)
    private String sourceType;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false, length = 160)
    private String locationName;
    private OffsetDateTime periodStart;
    private OffsetDateTime periodEnd;
    private String contentSha256;
    @Column(nullable = false)
    private Instant createdAt;
    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ObservationEntity> observations = new ArrayList<>();

    protected DatasetEntity() {}

    public DatasetEntity(UUID ownerId, String name, String sourceType, String locationName,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, String contentSha256) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.sourceType = sourceType;
        this.status = "READY";
        this.locationName = locationName;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.contentSha256 = contentSha256;
        this.createdAt = Instant.now();
    }

    public void addObservation(ObservationEntity observation) {
        observations.add(observation);
        observation.setDataset(this);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getSourceType() { return sourceType; }
    public String getStatus() { return status; }
    public String getLocationName() { return locationName; }
    public OffsetDateTime getPeriodStart() { return periodStart; }
    public OffsetDateTime getPeriodEnd() { return periodEnd; }
    public String getContentSha256() { return contentSha256; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ObservationEntity> getObservations() { return observations; }
}

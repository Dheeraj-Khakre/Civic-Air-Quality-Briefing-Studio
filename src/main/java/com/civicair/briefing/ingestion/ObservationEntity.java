package com.civicair.briefing.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dataset_row")
public class ObservationEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetEntity dataset;
    @Column(nullable = false)
    private int rowNumber;
    @Column(nullable = false, length = 20000)
    private String originalData;
    @Column(length = 40)
    private String pollutant;
    private OffsetDateTime observedAt;
    @Column(length = 160)
    private String locationName;
    private Double originalValue;
    @Column(length = 40)
    private String originalUnit;
    private Double normalizedValue;
    @Column(length = 40)
    private String normalizedUnit;
    @Column(nullable = false)
    private boolean duplicate;

    protected ObservationEntity() {}

    public ObservationEntity(int rowNumber, String originalData, String pollutant, OffsetDateTime observedAt,
            String locationName, Double originalValue, String originalUnit, Double normalizedValue,
            String normalizedUnit, boolean duplicate) {
        this.id = UUID.randomUUID();
        this.rowNumber = rowNumber;
        this.originalData = originalData;
        this.pollutant = pollutant;
        this.observedAt = observedAt;
        this.locationName = locationName;
        this.originalValue = originalValue;
        this.originalUnit = originalUnit;
        this.normalizedValue = normalizedValue;
        this.normalizedUnit = normalizedUnit;
        this.duplicate = duplicate;
    }

    void setDataset(DatasetEntity dataset) { this.dataset = dataset; }
    public UUID getId() { return id; }
    public DatasetEntity getDataset() { return dataset; }
    public int getRowNumber() { return rowNumber; }
    public String getOriginalData() { return originalData; }
    public String getPollutant() { return pollutant; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public String getLocationName() { return locationName; }
    public Double getOriginalValue() { return originalValue; }
    public String getOriginalUnit() { return originalUnit; }
    public Double getNormalizedValue() { return normalizedValue; }
    public String getNormalizedUnit() { return normalizedUnit; }
    public boolean isDuplicate() { return duplicate; }
}

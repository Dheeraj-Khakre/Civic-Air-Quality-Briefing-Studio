package com.civicair.briefing.analytics;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "finding")
public class FindingEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisEntity analysis;
    @Column(nullable = false, length = 48)
    private String category;
    @Column(nullable = false, length = 500)
    private String summary;
    @Column(length = 40)
    private String pollutant;
    @Column(name = "measurement_value")
    private Double value;
    @Column(length = 32)
    private String unit;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 2_000_000)
    private String evidenceIds;

    protected FindingEntity() {}

    public FindingEntity(String category, String summary, String pollutant, Double value, String unit, String evidenceIds) {
        this.id = UUID.randomUUID();
        this.category = category;
        this.summary = summary;
        this.pollutant = pollutant;
        this.value = value;
        this.unit = unit;
        this.evidenceIds = evidenceIds;
    }

    void setAnalysis(AnalysisEntity analysis) { this.analysis = analysis; }
    public UUID getId() { return id; }
    public AnalysisEntity getAnalysis() { return analysis; }
    public String getCategory() { return category; }
    public String getSummary() { return summary; }
    public String getPollutant() { return pollutant; }
    public Double getValue() { return value; }
    public String getUnit() { return unit; }
    public String getEvidenceIds() { return evidenceIds; }
}

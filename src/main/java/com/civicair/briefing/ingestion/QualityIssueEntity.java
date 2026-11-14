package com.civicair.briefing.ingestion;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_issue")
public class QualityIssueEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID datasetId;
    private Integer rowNumber;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 16)
    private String severity;
    @Column(nullable = false, length = 500)
    private String message;

    protected QualityIssueEntity() {}

    public QualityIssueEntity(UUID datasetId, Integer rowNumber, String code, String severity, String message) {
        this.id = UUID.randomUUID();
        this.datasetId = datasetId;
        this.rowNumber = rowNumber;
        this.code = code;
        this.severity = severity;
        this.message = message;
    }

    public UUID getId() { return id; }
    public UUID getDatasetId() { return datasetId; }
    public Integer getRowNumber() { return rowNumber; }
    public String getCode() { return code; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
}

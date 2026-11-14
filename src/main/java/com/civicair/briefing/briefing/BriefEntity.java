package com.civicair.briefing.briefing;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "brief")
public class BriefEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID ownerId;
    @Column(nullable = false)
    private UUID analysisId;
    @Column(nullable = false, length = 32)
    private String type;
    @Column(nullable = false, length = 32)
    private String audience;
    @Column(nullable = false, length = 8)
    private String language;
    @Column(nullable = false, length = 16)
    private String readingLevel;
    @Column(nullable = false, length = 20000)
    private String content;
    @Column(nullable = false, length = 20000)
    private String evidenceIds;
    @Column(nullable = false)
    private Instant createdAt;

    protected BriefEntity() {}

    public BriefEntity(UUID ownerId, UUID analysisId, String type, String audience, String language,
            String readingLevel, String content, String evidenceIds) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.analysisId = analysisId;
        this.type = type;
        this.audience = audience;
        this.language = language;
        this.readingLevel = readingLevel;
        this.content = content;
        this.evidenceIds = evidenceIds;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getAnalysisId() { return analysisId; }
    public String getType() { return type; }
    public String getAudience() { return audience; }
    public String getLanguage() { return language; }
    public String getReadingLevel() { return readingLevel; }
    public String getContent() { return content; }
    public String getEvidenceIds() { return evidenceIds; }
    public Instant getCreatedAt() { return createdAt; }
}

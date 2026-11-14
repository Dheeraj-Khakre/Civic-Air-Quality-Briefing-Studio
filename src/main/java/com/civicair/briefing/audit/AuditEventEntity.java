package com.civicair.briefing.audit;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity {
    @Id
    private UUID id;
    private UUID actorId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(nullable = false)
    private UUID resourceId;
    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {}

    public AuditEventEntity(UUID actorId, String action, UUID resourceId) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.action = action;
        this.resourceId = resourceId;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public UUID getResourceId() { return resourceId; }
    public Instant getOccurredAt() { return occurredAt; }
}

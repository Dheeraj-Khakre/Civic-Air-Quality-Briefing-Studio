package com.civicair.briefing.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    public void record(UUID actorId, String action, UUID resourceId) {
        events.save(new AuditEventEntity(actorId, action, resourceId));
    }
}

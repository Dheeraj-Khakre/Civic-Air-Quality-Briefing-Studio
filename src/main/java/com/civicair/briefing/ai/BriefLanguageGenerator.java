package com.civicair.briefing.ai;

import java.util.List;
import java.util.UUID;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.briefing.BriefService.BriefRequest;

public interface BriefLanguageGenerator {
    GeneratedBrief generate(String type, BriefRequest request, List<FindingEntity> evidence);

    record GeneratedBrief(String content, List<UUID> evidenceIds) {}
}

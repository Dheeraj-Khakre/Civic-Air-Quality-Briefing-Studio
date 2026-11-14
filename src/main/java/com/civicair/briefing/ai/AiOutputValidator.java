package com.civicair.briefing.ai;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiOutputValidator {
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?");
    private static final Pattern UNSUPPORTED_CLAIMS = Pattern.compile(
            "(?i)\\b(cure|treat|diagnos|safe|dangerous|asthma|symptom|risk|health|medical|cause|caused|trigger|emergency|respiratory|disease|exceedance|compliance|guarantee)\\w*\\b"
                    + "|स्वास्थ्य|खतरा|सुरक्षित|बीमारी|रोग|दमा|कारण|इलाज|लक्षण|चिकित्सा");
    private final ObjectMapper objectMapper;

    public AiOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(SpringAiBriefLanguageGenerator.AiBriefDraft draft, List<FindingEntity> findings) {
        List<UUID> permittedEvidence = findings.stream()
                .map(FindingEntity::getId).distinct().toList();
        if (draft == null || draft.content() == null || draft.content().isBlank()
                || draft.evidenceIds() == null || draft.evidenceIds().isEmpty()
                || !permittedEvidence.containsAll(draft.evidenceIds())
                || UNSUPPORTED_CLAIMS.matcher(draft.content()).find()
                || !numbersAreSupported(draft.content(), findings)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_OUTPUT_REJECTED",
                    "Generated brief did not pass evidence and claim validation.");
        }
    }

    private boolean numbersAreSupported(String content, List<FindingEntity> findings) {
        String numericText = content.replaceAll("(?i)PM\\s*2\\.5", "PM25")
                .replaceAll("(?i)/m3", "");
        Set<Double> allowed = findings.stream().map(FindingEntity::getValue)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<Integer> counts = findings.stream().map(finding -> readEvidence(finding.getEvidenceIds()).size())
                .collect(java.util.stream.Collectors.toSet());
        Matcher matcher = NUMBER.matcher(numericText);
        while (matcher.find()) {
            try {
                double numeric = Double.parseDouble(matcher.group().replace(',', '.'));
                boolean knownMeasurement = allowed.stream().anyMatch(value -> Math.abs(value - numeric) < 0.0005);
                boolean knownCount = counts.contains((int) numeric) && numeric == (int) numeric;
                if (!knownMeasurement && !knownCount) return false;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private List<UUID> readEvidence(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }
}

package com.civicair.briefing.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.briefing.BriefService.BriefRequest;
import com.civicair.briefing.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Component
@ConditionalOnProperty(name = "civic.ai.enabled", havingValue = "true")
public class SpringAiBriefLanguageGenerator implements BriefLanguageGenerator {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiOutputValidator validator;

    public SpringAiBriefLanguageGenerator(ChatClient.Builder builder, ObjectMapper objectMapper,
            AiOutputValidator validator) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public GeneratedBrief generate(String type, BriefRequest request, List<FindingEntity> findings) {
        String context;
        try {
            context = objectMapper.writeValueAsString(findings.stream().map(finding -> new FindingInput(
                    finding.getId(), finding.getPollutant(), finding.getValue(), finding.getUnit(),
                    readEvidence(finding.getEvidenceIds()).size())).toList());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not prepare verified findings for generation.", exception);
        }
        AiBriefDraft draft;
        try {
            draft = chatClient.prompt()
                    .system("Write a concise civic air-quality brief using only the supplied verified findings. "
                            + "Do not infer causes, regulatory exceedances, health effects, actions or measurements. "
                            + "Use only the requested language and reading level. Return structured content and the exact evidence UUIDs used.")
                    .user("Type: " + type + "\nAudience: " + request.audience() + "\nLanguage: " + request.language()
                            + "\nReading level: " + request.readingLevel() + "\nVerified findings: " + context)
                    .call().entity(AiBriefDraft.class);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                    "The configured language model did not return a usable brief.");
        }
        validator.validate(draft, findings);
        return new GeneratedBrief(draft.content(), draft.evidenceIds().stream().distinct().toList());
    }

    private List<UUID> readEvidence(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }

    public record AiBriefDraft(@NotBlank String content, @NotEmpty List<UUID> evidenceIds) {}
    private record FindingInput(UUID evidenceId, String pollutant, Double value, String unit, int observationCount) {}
}

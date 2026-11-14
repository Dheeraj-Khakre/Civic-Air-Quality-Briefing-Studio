package com.civicair.briefing.ai;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.briefing.BriefService.BriefRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "civic.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DeterministicBriefLanguageGenerator implements BriefLanguageGenerator {
    private final ObjectMapper objectMapper;

    public DeterministicBriefLanguageGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedBrief generate(String type, BriefRequest request, List<FindingEntity> findings) {
        boolean hindi = request.language().equals("hi");
        boolean simple = request.readingLevel().equals("SIMPLE");
        StringBuilder text = new StringBuilder();
        if (hindi) {
            text.append(type.equals("INTERNAL") ? "आंतरिक वायु गुणवत्ता सारांश\n" : "नागरिक वायु गुणवत्ता सूचना\n");
            text.append("यह सारांश जमा किए गए आंकड़ों के वर्णनात्मक विश्लेषण पर आधारित है।\n");
            for (FindingEntity finding : findings) {
                text.append(finding.getPollutant()).append(": औसत ")
                        .append(String.format(Locale.ROOT, "%.3f", finding.getValue())).append(" ug/m3; ")
                        .append(evidence(finding.getEvidenceIds()).size()).append(" माप।\n");
            }
            text.append("यह AQI या स्वास्थ्य जोखिम का आकलन नहीं है। अधिक स्थानीय माप और सत्यापित AQI मानक के बिना अधिक जानकारी आवश्यक है।\n")
                    .append("बच्चों, वृद्धजनों और वायु प्रदूषण के प्रति संवेदनशील लोगों के लिए स्थानीय प्राधिकरण की सत्यापित सलाह देखें।");
        } else {
            text.append(type.equals("INTERNAL") ? "Internal air-quality briefing\n" : "Citizen air-quality advisory\n");
            text.append(simple ? "This summary describes measurements in the submitted dataset.\n"
                    : "This briefing reports deterministic descriptive statistics from validated observations in the submitted dataset.\n");
            for (FindingEntity finding : findings) {
                text.append(finding.getPollutant()).append(": average ")
                        .append(String.format(Locale.ROOT, "%.3f", finding.getValue())).append(" ug/m3 across ")
                        .append(evidence(finding.getEvidenceIds()).size()).append(" measurements.\n");
            }
            text.append("\nThis is not an AQI or health-risk assessment. More local measurements and a configured, documented AQI standard are needed for those conclusions.")
                    .append("\nChildren, older adults, and people sensitive to poor air should follow verified guidance from local public-health authorities.");
        }
        return new GeneratedBrief(text.toString(), findings.stream().map(FindingEntity::getId).distinct().toList());
    }

    private List<UUID> evidence(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored evidence references are invalid.", exception); }
    }
}

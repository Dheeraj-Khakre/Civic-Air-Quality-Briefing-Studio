package com.civicair.briefing.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.civicair.briefing.analytics.FindingEntity;
import com.civicair.briefing.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiOutputValidatorTest {
    private final AiOutputValidator validator = new AiOutputValidator(new ObjectMapper());
    private final FindingEntity finding = new FindingEntity("POLLUTANT_AVERAGE", "PM2.5 average", "PM2.5",
            10.0, "ug/m3", "[\"" + UUID.randomUUID() + "\"]");
    private final UUID evidenceId = finding.getId();

    @Test
    void acceptsSourcedTextUsingOnlyVerifiedValues() {
        var draft = new SpringAiBriefLanguageGenerator.AiBriefDraft(
                "PM2.5 average is 10.000 ug/m3, based on 1 observation.", List.of(evidenceId));
        validator.validate(draft, List.of(finding));
        assertThat(draft.evidenceIds()).containsExactly(evidenceId);
    }

    @Test
    void rejectsUnsupportedNumericalClaimsAndEvidence() {
        assertRejected("PM2.5 average is 999 ug/m3.", List.of(evidenceId));
        assertRejected("PM2.5 average is 10 ug/m3.", List.of(UUID.randomUUID()));
    }

    @Test
    void rejectsUnsupportedHealthClaims() {
        assertRejected("The measurements are dangerous for children.", List.of(evidenceId));
    }

    private void assertRejected(String content, List<UUID> evidenceIds) {
        var draft = new SpringAiBriefLanguageGenerator.AiBriefDraft(content, evidenceIds);
        assertThatThrownBy(() -> validator.validate(draft, List.of(finding)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("evidence and claim validation");
    }
}

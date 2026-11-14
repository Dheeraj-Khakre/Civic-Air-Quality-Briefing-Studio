package com.civicair.briefing.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ObservationNormalizerTest {
    private final ObservationNormalizer normalizer = new ObservationNormalizer();

    @Test
    void canonicalizesCommonHeadersAndPollutantAliases() {
        assertThat(normalizer.canonicalFields(java.util.Map.of(
                "Date and Time", "2026-03-01T10:00:00Z",
                "Station Name", "Ward 4",
                "Concentration", "0.02",
                "Units", "mg/m3")))
                .containsEntry("timestamp", "2026-03-01T10:00:00Z")
                .containsEntry("location", "Ward 4")
                .containsEntry("value", "0.02")
                .containsEntry("unit", "mg/m3");
        assertThat(normalizer.canonicalPollutant(" PM2.5 (µg/m³) ")).isEqualTo("PM2.5");
        assertThat(normalizer.canonicalPollutant("nitrogen dioxide")).isEqualTo("NO2");
        assertThat(normalizer.canonicalPollutant("unknown compound")).isNull();
    }

    @Test
    void convertsOnlySupportedMassUnitsAndRetainsUnknownUnitsAsUnsupported() {
        assertThat(normalizer.normalizeUnit("PM2.5", "mg/m³", 0.02))
                .isEqualTo(new ObservationNormalizer.NormalizedMeasurement(20.0, "ug/m3", true));
        assertThat(normalizer.normalizeUnit("PM10", "ppm", 10.0))
                .isEqualTo(new ObservationNormalizer.NormalizedMeasurement(null, null, false));
        assertThat(normalizer.normalizeUnit("PM10", "ug/m3", null))
                .isEqualTo(new ObservationNormalizer.NormalizedMeasurement(null, null, false));
    }

    @Test
    void parsesLocalTimesAsUtcAndRejectsInvalidNumbersAndDates() {
        assertThat(normalizer.parseTimestamp("2026/03/01 10:30"))
                .isEqualTo(OffsetDateTime.parse("2026-03-01T10:30:00Z"));
        assertThat(normalizer.parseNumber("NaN")).isNull();
        assertThat(normalizer.parseNumber("Infinity")).isNull();
        assertThat(normalizer.parseNumber("not-a-number")).isNull();
        assertThat(normalizer.parseTimestamp("2026-99-99T25:00")).isNull();
    }
}

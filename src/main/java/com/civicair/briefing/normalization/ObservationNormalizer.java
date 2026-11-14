package com.civicair.briefing.normalization;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ObservationNormalizer {
    private static final Map<String, String> POLLUTANTS = Map.ofEntries(
            Map.entry("pm25", "PM2.5"), Map.entry("pm2.5", "PM2.5"), Map.entry("pm2_5", "PM2.5"),
            Map.entry("pm10", "PM10"), Map.entry("no2", "NO2"), Map.entry("nitrogendioxide", "NO2"),
            Map.entry("so2", "SO2"), Map.entry("sulfurdioxide", "SO2"), Map.entry("sulphurdioxide", "SO2"),
            Map.entry("o3", "O3"), Map.entry("ozone", "O3"), Map.entry("co", "CO"), Map.entry("carbonmonoxide", "CO"));
    private static final Map<String, String> COLUMN_ALIASES = Map.ofEntries(
            Map.entry("timestamp", "timestamp"), Map.entry("datetime", "timestamp"),
            Map.entry("date", "timestamp"), Map.entry("time", "timestamp"),
            Map.entry("observedat", "timestamp"), Map.entry("measurementtime", "timestamp"),
            Map.entry("dateandtime", "timestamp"), Map.entry("sampledate", "timestamp"),
            Map.entry("location", "location"), Map.entry("locationname", "location"),
            Map.entry("station", "location"), Map.entry("stationname", "location"),
            Map.entry("monitoringstation", "location"), Map.entry("site", "location"),
            Map.entry("ward", "location"), Map.entry("wardname", "location"), Map.entry("area", "location"),
            Map.entry("pollutant", "pollutant"), Map.entry("parameter", "pollutant"),
            Map.entry("species", "pollutant"), Map.entry("pollutantname", "pollutant"),
            Map.entry("measurement", "pollutant"),
            Map.entry("value", "value"), Map.entry("reading", "value"),
            Map.entry("concentration", "value"), Map.entry("concentrationvalue", "value"),
            Map.entry("level", "value"), Map.entry("unit", "unit"), Map.entry("units", "unit"),
            Map.entry("unitofmeasurement", "unit"), Map.entry("concentrationunit", "unit"));

    public Map<String, String> canonicalFields(Map<String, String> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((header, value) -> {
            String canonical = COLUMN_ALIASES.get(normalizeHeader(header));
            if (canonical != null && !result.containsKey(canonical)) result.put(canonical, value);
        });
        return result;
    }

    public String normalizeHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]", "");
    }

    public String canonicalPollutant(String value) {
        if (value == null) return null;
        String key = value.toLowerCase(Locale.ROOT).replace("μ", "u").replace("µ", "u")
                .replaceAll("\\s*\\([^)]*\\)", "").replaceAll("[^a-z0-9._]", "");
        return POLLUTANTS.get(key);
    }

    public NormalizedMeasurement normalizeUnit(String pollutant, String rawUnit, Double value) {
        if (value == null || pollutant == null || blank(rawUnit)) return new NormalizedMeasurement(null, null, false);
        String unit = rawUnit.toLowerCase(Locale.ROOT).replace("μ", "u").replace("µ", "u")
                .replace("³", "3").replace("^", "").replace(" ", "").replace("per", "/");
        if (unit.equals("ug/m3") || unit.equals("ugm-3") || unit.equals("mcg/m3")) {
            return new NormalizedMeasurement(value, "ug/m3", true);
        }
        if (unit.equals("mg/m3") || unit.equals("mgm-3")) {
            return new NormalizedMeasurement(value * 1000.0, "ug/m3", true);
        }
        return new NormalizedMeasurement(null, null, false);
    }

    public OffsetDateTime parseTimestamp(String value) {
        if (blank(value)) return null;
        try { return OffsetDateTime.parse(value.trim()); } catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME).atOffset(ZoneOffset.UTC); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(value.trim()).atStartOfDay().atOffset(ZoneOffset.UTC); }
        catch (DateTimeParseException ignored) { }
        for (String pattern : List.of("d/M/uuuu H:mm", "d/M/uuuu H:mm:ss", "M/d/uuuu H:mm", "uuuu/M/d H:mm")) {
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern(pattern, Locale.ROOT)).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    public String timestampFormat(String value) {
        String text = value.trim();
        if (text.endsWith("Z") || text.matches(".*[+-]\\d{2}:?\\d{2}$")) return "OFFSET_DATE_TIME";
        if (text.matches("\\d{4}-\\d{2}-\\d{2}")) return "ISO_DATE";
        if (text.matches("\\d{4}-\\d{2}-\\d{2}T.*")) return "ISO_LOCAL_DATE_TIME";
        return "LOCAL_DATE_TIME";
    }

    public Double parseNumber(String value) {
        if (blank(value)) return null;
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) { return null; }
    }

    public String cleanText(String value, int maxLength) {
        String cleaned = (value == null ? "" : value.trim()).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record NormalizedMeasurement(Double value, String unit, boolean supported) {}
}

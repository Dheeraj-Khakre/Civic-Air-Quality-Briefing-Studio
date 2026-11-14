package com.civicair.briefing.ingestion;

import static org.apache.commons.csv.CSVFormat.DEFAULT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.civicair.briefing.common.ApiException;

@Component
public class TabularDataParser {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ROWS = 50_000;
    private static final int MAX_COLUMNS = 500;

    public ParsedFile parseUpload(byte[] bytes) throws IOException {
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            ZipSecureFile.setMaxEntrySize(MAX_BYTES);
            ZipSecureFile.setMaxTextSize(MAX_BYTES / 2);
            ZipSecureFile.setMaxFileCount(1_000);
            Workbook workbook;
            try {
                workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid XLSX", exception);
            }
            try (workbook) {
                return new ParsedFile("XLSX", parseWorkbook(workbook));
            }
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Unsupported text encoding", exception);
        }
        if (text.indexOf('\0') >= 0) throw new IOException("Binary upload is not a CSV");
        ParsedFile parsed = parseCsv(text, detectDelimiter(text));
        if (parsed.rows().isEmpty()) throw new IOException("No rows");
        return new ParsedFile("CSV", parsed.rows());
    }

    public ParsedFile parsePasted(String text, char delimiter) {
        return parseCsv(text, delimiter);
    }

    private ParsedFile parseCsv(String text, char delimiter) {
        try (CSVParser parser = DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true)
                .setTrim(false).get().parse(new java.io.StringReader(text))) {
            List<CSVRecord> records = parser.getRecords();
            if (records.size() < 2) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TABULAR_DATA",
                    "Provide a header row and at least one data row.");
            List<String> headers = new ArrayList<>();
            records.getFirst().forEach(headers::add);
            if (headers.size() > MAX_COLUMNS || records.size() - 1 > MAX_ROWS) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "DATASET_TOO_LARGE",
                        "Dataset exceeds the supported row or column limit.");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                Map<String, String> row = new LinkedHashMap<>();
                for (int col = 0; col < headers.size(); col++) {
                    row.put(headers.get(col), col < record.size() ? record.get(col) : "");
                }
                rows.add(row);
            }
            return new ParsedFile("CSV", rows);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TABULAR_DATA", "Unable to parse delimited tabular data.");
        }
    }

    private List<Map<String, String>> parseWorkbook(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_WORKBOOK", "Workbook has no sheets.");
        }
        var sheet = workbook.getSheetAt(0);
        var iterator = sheet.rowIterator();
        if (!iterator.hasNext()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_WORKBOOK", "Workbook is empty.");
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Row headerRow = iterator.next();
        List<String> headers = new ArrayList<>();
        headerRow.forEach(cell -> headers.add(formatter.formatCellValue(cell)));
        if (headers.size() > MAX_COLUMNS || sheet.getLastRowNum() > MAX_ROWS) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "DATASET_TOO_LARGE",
                    "Dataset exceeds the supported row or column limit.");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                var cell = row.getCell(i);
                values.put(headers.get(i), cell == null ? "" : formatter.formatCellValue(cell));
            }
            rows.add(values);
        }
        return rows;
    }

    private char detectDelimiter(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        return List.of(',', ';', '\t').stream()
                .max(java.util.Comparator.comparingLong(delimiter -> count(firstLine, delimiter)))
                .filter(delimiter -> count(firstLine, delimiter) > 0).orElse(',');
    }

    private long count(String text, char character) {
        return text.chars().filter(ch -> ch == character).count();
    }

    public record ParsedFile(String type, List<Map<String, String>> rows) {}
}

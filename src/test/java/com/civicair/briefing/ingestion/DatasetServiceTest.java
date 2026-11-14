package com.civicair.briefing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import com.civicair.briefing.audit.AuditService;
import com.civicair.briefing.ingestion.DatasetController.PastedDatasetRequest;
import com.civicair.briefing.normalization.ObservationNormalizer;
import com.civicair.briefing.validation.DatasetQualityValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

class DatasetServiceTest {
    private DatasetRepository datasets;
    private QualityIssueRepository issues;
    private AuditService audit;
    private DatasetService service;

    @BeforeEach
    void setUp() {
        datasets = mock(DatasetRepository.class);
        issues = mock(QualityIssueRepository.class);
        audit = mock(AuditService.class);
        when(datasets.save(any(DatasetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(issues.saveAll(any())).thenAnswer(invocation -> new ArrayList<>());
        service = new DatasetService(datasets, issues, new ObjectMapper(), audit,
                new ObservationNormalizer(), new DatasetQualityValidator(), new TabularDataParser());
    }

    @Test
    void normalizesKnownMassUnitsAndPreservesUnknownUnitsAsIssues() {
        UUID owner = UUID.randomUUID();
        PastedDatasetRequest request = new PastedDatasetRequest("sensors", "Ward 1", null, null, ",",
                "date,pollutant,value,unit,location\n"
                        + "2026-01-01T10:00:00Z,PM2.5,0.03,mg/m3,Station A\n"
                        + "2026-01-01T11:00:00Z,PM10,15,ppm,Station A");

        var result = service.pasted(request, owner);
        var capture = org.mockito.ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasets).save(capture.capture());
        List<ObservationEntity> rows = capture.getValue().getObservations();

        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(rows.get(0).getNormalizedValue()).isEqualTo(30.0);
        assertThat(rows.get(0).getNormalizedUnit()).isEqualTo("ug/m3");
        assertThat(rows.get(1).getOriginalUnit()).isEqualTo("ppm");
        assertThat(rows.get(1).getNormalizedValue()).isNull();
        verify(issues).saveAll(any());
        verify(audit).record(owner, "DATASET_INGESTED", result.id());
    }

    @Test
    void recordsMissingAndDuplicateMeasurementsWithoutFillingZero() {
        PastedDatasetRequest request = new PastedDatasetRequest("sensors", "Ward 1", null, null, ",",
                "timestamp,pollutant,value,unit\n"
                        + "2026-01-01T10:00:00Z,PM2.5,,ug/m3\n"
                        + "2026-01-01T10:00:00Z,PM2.5,5,ug/m3");

        service.pasted(request, UUID.randomUUID());
        var capture = org.mockito.ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasets).save(capture.capture());
        assertThat(capture.getValue().getObservations()).hasSize(2);
        assertThat(capture.getValue().getObservations().get(0).getNormalizedValue()).isNull();
        assertThat(capture.getValue().getObservations().get(1).isDuplicate()).isTrue();
    }

    @Test
    void parsesXlsxByContentAndRejectsSpoofedWorkbookContent() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("readings");
            var header = sheet.createRow(0);
            List<String> headers = List.of("timestamp", "pollutant", "value", "unit");
            for (int i = 0; i < headers.size(); i++) header.createCell(i).setCellValue(headers.get(i));
            var reading = sheet.createRow(1);
            reading.createCell(0).setCellValue("2026-01-01T10:00:00Z");
            reading.createCell(1).setCellValue("PM10");
            reading.createCell(2).setCellValue(7);
            reading.createCell(3).setCellValue("ug/m3");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }
        MultipartFile workbook = new MockMultipartFile("file", "not-an-xlsx.csv", "text/csv", workbookBytes);
        var result = service.upload(workbook, "Ward 1", null, null, UUID.randomUUID());
        assertThat(result.sourceType()).isEqualTo("XLSX");

        MultipartFile spoofed = new MockMultipartFile("file", "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "PKnot-a-workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.upload(spoofed, "Ward 1", null, null, UUID.randomUUID()))
                .isInstanceOf(com.civicair.briefing.common.ApiException.class)
                .hasMessageContaining("readable CSV or XLSX");
    }

    @Test
    void normalizesWideHeadersAndFlagsButRetainsOutliers() {
        String content = "Date;PM2.5 (µg/m³);Station;Units\n"
                + "2026/01/01 10:00;1;Ward A;ug/m3\n"
                + "2026/01/01 11:00;2;Ward A;ug/m3\n"
                + "2026/01/01 12:00;3;Ward A;ug/m3\n"
                + "2026/01/01 13:00;4;Ward A;ug/m3\n"
                + "2026/01/01 14:00;100;Ward A;ug/m3";
        service.pasted(new PastedDatasetRequest("wide", "Ward A", null, null, ";", content), UUID.randomUUID());

        var savedDataset = org.mockito.ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasets).save(savedDataset.capture());
        assertThat(savedDataset.getValue().getObservations()).hasSize(5);
        assertThat(savedDataset.getValue().getObservations().getLast().getNormalizedValue()).isEqualTo(100.0);
        org.mockito.ArgumentCaptor<Iterable<QualityIssueEntity>> savedIssues =
                org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(issues).saveAll(savedIssues.capture());
        assertThat(java.util.stream.StreamSupport.stream(savedIssues.getValue().spliterator(), false)
                .map(QualityIssueEntity::getCode).toList()).contains("POTENTIAL_OUTLIER");
    }
}

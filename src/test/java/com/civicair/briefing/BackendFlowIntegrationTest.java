package com.civicair.briefing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendFlowIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void registersUploadsAnalyzesAndGeneratesEvidenceLinkedBrief() throws Exception {
        MvcResult registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"flow@example.test\",\"password\":\"long-password-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode registered = objectMapper.readTree(registration.getResponse().getContentAsString());
        String token = registered.get("accessToken").asText();

        byte[] csv = ("timestamp,pollutant,value,unit,location\n"
                + "2026-01-01T10:00:00Z,PM2.5,10,ug/m3,Station A\n"
                + "2026-01-01T11:00:00Z,PM2.5,20,ug/m3,Station A\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "air.csv", "text/csv", csv);
        MvcResult uploaded = mvc.perform(multipart("/api/v1/datasets").file(file)
                        .param("locationName", "Ward 1")
                        .param("periodStart", "2026-01-01T00:00:00Z")
                        .param("periodEnd", "2026-01-02T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowCount").value(2))
                .andReturn();
        String datasetId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();

        MvcResult analyzed = mvc.perform(post("/api/v1/datasets/{datasetId}/analyses", datasetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"standard\":\"DESCRIPTIVE_V1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.metrics[0].average").value(15.0))
                .andReturn();
        String analysisId = objectMapper.readTree(analyzed.getResponse().getContentAsString()).get("id").asText();

        MvcResult brief = mvc.perform(post("/api/v1/analyses/{analysisId}/citizen-advisories", analysisId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"SCHOOL\",\"language\":\"en\",\"readingLevel\":\"SIMPLE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceIds.length()").value(1))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("not an AQI")))
                .andReturn();
        String briefId = objectMapper.readTree(brief.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/v1/briefs/{briefId}", briefId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId));
        mvc.perform(get("/api/v1/datasets/{datasetId}/quality-report", datasetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(2));

        MvcResult anotherAccount = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.test\",\"password\":\"long-password-456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String otherToken = objectMapper.readTree(anotherAccount.getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(get("/api/v1/datasets/{datasetId}", datasetId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/datasets").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/analyses/{analysisId}/recommendations", analysisId)
                        .param("audience", "WARD_OFFICER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}

package com.civicair.briefing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthenticationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void localAdminCanLoginAndUseAudienceScopedFeaturesWithoutCrossingResourceOwnership() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@civic-air.test\",\"password\":\"test-only-admin-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();
        String adminToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult dataset = mvc.perform(post("/api/v1/datasets/pasted")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin test readings","locationName":"Ward 8","delimiter":",","content":"timestamp,pollutant,value,unit,location\\n2026-04-01T10:00:00Z,PM2.5,12,ug/m3,Station A"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        String datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asText();

        MvcResult analysis = mvc.perform(post("/api/v1/datasets/{datasetId}/analyses", datasetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"standard\":\"DESCRIPTIVE_V1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String analysisId = objectMapper.readTree(analysis.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/analyses/{analysisId}/briefs", analysisId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"SCHOOL\",\"language\":\"en\",\"readingLevel\":\"SIMPLE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audience").value("SCHOOL"))
                .andExpect(jsonPath("$.evidenceIds.length()").value(1));

        MvcResult schoolRegistration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"school-ownership@example.test\",\"password\":\"long-password-abc\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode school = objectMapper.readTree(schoolRegistration.getResponse().getContentAsString());
        String schoolToken = school.get("accessToken").asText();
        mvc.perform(post("/api/v1/datasets/{datasetId}/analyses", datasetId)
                        .header("Authorization", "Bearer " + schoolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"standard\":\"DESCRIPTIVE_V1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrationRejectsDuplicateEmailsAndLoginReturnsProblemForBadCredentials() throws Exception {
        String email = "duplicate-" + java.util.UUID.randomUUID() + "@example.test";
        String registration = "{\"email\":\"" + email + "\",\"password\":\"long-password-abc\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_EXISTS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password-abc\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }
}

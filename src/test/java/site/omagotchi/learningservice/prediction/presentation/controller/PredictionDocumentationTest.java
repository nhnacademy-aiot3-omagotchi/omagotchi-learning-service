package site.omagotchi.learningservice.prediction.presentation.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.prediction.application.StudyTimePredictionService;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(PredictionController.class)
@LearningRestDocsTest
class PredictionDocumentationTest {
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final String AUTHORIZATION = "Bearer " + TestJwtKeyConfig.issue();

    @MockitoBean
    private StudyTimePredictionService predictionService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공부 시간 예측")
    void predictsStudyTime() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(predictionService.predict(eq(USER_ID), eq(10L), anyString()))
                .willReturn(new StudyTimePredictionResult(4.25, "study-time-v1"));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/predictions/study-time", 10L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "prediction/study-time",
                        pathParameters(parameterWithName("cohort-id").description("예측 대상 기수 ID")),
                        responseFields(
                                fieldWithPath("predictedStudyHours")
                                        .type(JsonFieldType.NUMBER)
                                        .description("예측 공부 시간 (시간)"),
                                fieldWithPath("modelVersion")
                                        .type(JsonFieldType.STRING)
                                        .description("예측 모델 버전"))))
                .andExpect(jsonPath("$.predictedStudyHours").value(4.25))
                .andExpect(jsonPath("$.modelVersion").value("study-time-v1"));
    }
}

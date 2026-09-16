package site.omagotchi.learningservice.study.presentation.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.study.application.TimerCommandService;
import site.omagotchi.learningservice.study.application.TimerQueryService;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@DisplayName("타이머 API")
@WebMvcTest(controllers = TimerController.class)
@LearningRestDocsTest
class TimerControllerTest {

    private static final Long COHORT_ID = 10L;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TIMER_RUN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");

    @MockitoBean
    private TimerCommandService timerCommandService;

    @MockitoBean
    private TimerQueryService timerQueryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("타이머 시작")
    class Start {

        @Test
        @DisplayName("정상 처리")
        void startsTimer() throws Exception {
            TimerStateResult result = TimerStateResult.running(TIMER_RUN_ID, STARTED_AT, 0L);
            given(timerCommandService.start(USER_ID, COHORT_ID))
                    .willReturn(result);

            mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/timer/start", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isCreated())
                    .andDo(document(
                            "timer/start-timer",
                            pathParameters(parameterWithName("cohort-id").description("타이머를 시작할 기수 ID")),
                            responseFields(
                                    fieldWithPath("resultCode")
                                            .type(JsonFieldType.STRING)
                                            .description("처리 결과 코드"),
                                    fieldWithPath("timerRunId")
                                            .type(JsonFieldType.STRING)
                                            .description("생성된 타이머 실행 ID"),
                                    fieldWithPath("state")
                                            .type(JsonFieldType.STRING)
                                            .description("타이머 상태"),
                                    fieldWithPath("startedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("타이머 시작 시각 (ISO-8601)"),
                                    fieldWithPath("elapsedSeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("경과 시간 (초)"))))
                    .andExpect(jsonPath("$.resultCode").value("TIMER_STARTED"))
                    .andExpect(jsonPath("$.timerRunId").value(TIMER_RUN_ID.toString()))
                    .andExpect(jsonPath("$.state").value("RUNNING"))
                    .andExpect(jsonPath("$.startedAt").value(STARTED_AT.toString()))
                    .andExpect(jsonPath("$.elapsedSeconds").value(0L));

            verify(timerCommandService).start(USER_ID, COHORT_ID);
        }
    }

    @Nested
    @DisplayName("타이머 상태 조회")
    class GetCurrent {

        @Test
        @DisplayName("실행 중 응답")
        void returnsRunningTimer() throws Exception {
            TimerStateResult result = TimerStateResult.running(TIMER_RUN_ID, STARTED_AT, 125L);
            given(timerQueryService.getCurrent(USER_ID, COHORT_ID)).willReturn(result);

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/timer", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isOk())
                    .andDo(document(
                            "timer/get-running-timer",
                            pathParameters(parameterWithName("cohort-id").description("타이머 상태를 조회할 기수 ID")),
                            responseFields(
                                    fieldWithPath("state")
                                            .type(JsonFieldType.STRING)
                                            .description("타이머 상태"),
                                    fieldWithPath("timerRunId")
                                            .type(JsonFieldType.STRING)
                                            .description("실행 중인 타이머 ID"),
                                    fieldWithPath("startedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("타이머 시작 시각 (ISO-8601)"),
                                    fieldWithPath("elapsedSeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("경과 시간 (초)"))))
                    .andExpect(jsonPath("$.state").value("RUNNING"))
                    .andExpect(jsonPath("$.timerRunId").value(TIMER_RUN_ID.toString()))
                    .andExpect(jsonPath("$.startedAt").value(STARTED_AT.toString()))
                    .andExpect(jsonPath("$.elapsedSeconds").value(125L));

            verify(timerQueryService).getCurrent(USER_ID, COHORT_ID);
        }

        @Test
        @DisplayName("실행 없음 응답")
        void returnsStoppedWhenNoActiveTimerExists() throws Exception {
            given(timerQueryService.getCurrent(USER_ID, COHORT_ID))
                    .willReturn(TimerStateResult.stopped());

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/timer", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isOk())
                    .andDo(document(
                            "timer/get-stopped-timer",
                            pathParameters(parameterWithName("cohort-id").description("타이머 상태를 조회할 기수 ID")),
                            responseFields(
                                    fieldWithPath("state")
                                            .type(JsonFieldType.STRING)
                                            .description("타이머 상태"),
                                    fieldWithPath("timerRunId")
                                            .type(JsonFieldType.STRING)
                                            .optional()
                                            .description("실행 중인 타이머 ID (실행 중인 타이머가 없으면 null)"),
                                    fieldWithPath("startedAt")
                                            .type(JsonFieldType.STRING)
                                            .optional()
                                            .description("타이머 시작 시각 (실행 중인 타이머가 없으면 null)"),
                                    fieldWithPath("elapsedSeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("경과 시간 (초)"))))
                    .andExpect(jsonPath("$.state").value("STOPPED"))
                    .andExpect(jsonPath("$.timerRunId").value(nullValue()))
                    .andExpect(jsonPath("$.startedAt").value(nullValue()))
                    .andExpect(jsonPath("$.elapsedSeconds").value(0L));

            verify(timerQueryService).getCurrent(USER_ID, COHORT_ID);
        }
    }

    @Nested
    @DisplayName("타이머 폐기")
    class Discard {

        @Test
        @DisplayName("정상 처리")
        void discardsTimer() throws Exception {
            mockMvc.perform(post(
                                    "/api/v1/cohorts/{cohort-id}/timer/{timer-run-id}/discard",
                                    COHORT_ID,
                                    TIMER_RUN_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isNoContent())
                    .andDo(document(
                            "timer/discard-timer",
                            pathParameters(
                                    parameterWithName("cohort-id")
                                            .description("타이머가 속한 기수 ID"),
                                    parameterWithName("timer-run-id")
                                            .description("폐기할 타이머 실행 ID"))));

            verify(timerCommandService).discard(USER_ID, COHORT_ID, TIMER_RUN_ID);
        }
    }

    @Nested
    @DisplayName("타이머 종료")
    class Stop {

        @Test
        @DisplayName("정상 처리")
        void stopsTimerSuccessfully() throws Exception {
            mockMvc.perform(post(
                                    "/api/v1/cohorts/{cohort-id}/timer/{timer-run-id}/stop",
                                    COHORT_ID,
                                    TIMER_RUN_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isNoContent())
                    .andDo(document(
                            "timer/stop-timer",
                            pathParameters(
                                    parameterWithName("cohort-id")
                                            .description("타이머가 속한 기수 ID"),
                                    parameterWithName("timer-run-id")
                                            .description("종료할 타이머 실행 ID"))));

            verify(timerCommandService).stop(USER_ID, COHORT_ID, TIMER_RUN_ID);
        }
    }

    private static String bearerToken() {
        return "Bearer "
                + TestJwtKeyConfig.issue(
                        TestJwtKeyConfig.ISSUER,
                        TestJwtKeyConfig.AUDIENCE,
                        USER_ID.toString(),
                        "USER");
    }
}

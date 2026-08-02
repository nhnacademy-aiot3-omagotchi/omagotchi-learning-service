package site.omagotchi.learningservice.study.presentation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.study.application.TimerCommandService;
import site.omagotchi.learningservice.study.application.TimerQueryService;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("타이머 API")
@ExtendWith(MockitoExtension.class)
class TimerControllerTest {

    private static final Long COHORT_ID = 10L;
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID TIMER_RUN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Mock
    private TimerCommandService timerCommandService;

    @Mock
    private TimerQueryService timerQueryService;

    @InjectMocks
    private TimerController timerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(timerController).build();
    }

    @Nested
    @DisplayName("시작")
    class Start {

        @Test
        @DisplayName("정상 처리")
        void startsTimer() throws Exception {
            TimerStateResult result = TimerStateResult.running(
                    TIMER_RUN_ID,
                    STARTED_AT,
                    0L
            );
            given(timerCommandService.start(COMMAND_ID, USER_ID, COHORT_ID))
                    .willReturn(result);

            mockMvc.perform(post(
                            "/api/v1/cohorts/{cohortId}/timer/start",
                            COHORT_ID
                    )
                            .header("X-User-Id", USER_ID)
                            .header("X-Command-Id", COMMAND_ID))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resultCode").value("TIMER_STARTED"))
                    .andExpect(jsonPath("$.timerRunId").value(TIMER_RUN_ID.toString()))
                    .andExpect(jsonPath("$.state").value("RUNNING"))
                    .andExpect(jsonPath("$.startedAt").value(STARTED_AT.toString()))
                    .andExpect(jsonPath("$.elapsedSeconds").value(0L));

            verify(timerCommandService).start(COMMAND_ID, USER_ID, COHORT_ID);
        }
    }

    @Nested
    @DisplayName("현재 상태 조회")
    class GetCurrent {

        @Test
        @DisplayName("실행 중 정상 처리")
        void returnsRunningTimer() throws Exception {
            TimerStateResult result = TimerStateResult.running(
                    TIMER_RUN_ID,
                    STARTED_AT,
                    125L
            );
            given(timerQueryService.getCurrent(USER_ID, COHORT_ID)).willReturn(result);

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/timer",
                            COHORT_ID
                    )
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("RUNNING"))
                    .andExpect(jsonPath("$.timerRunId").value(TIMER_RUN_ID.toString()))
                    .andExpect(jsonPath("$.startedAt").value(STARTED_AT.toString()))
                    .andExpect(jsonPath("$.elapsedSeconds").value(125L));

            verify(timerQueryService).getCurrent(USER_ID, COHORT_ID);
        }

        @Test
        @DisplayName("활성 실행 없음 처리")
        void returnsStoppedWhenNoActiveTimerExists() throws Exception {
            given(timerQueryService.getCurrent(USER_ID, COHORT_ID))
                    .willReturn(TimerStateResult.stopped());

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/timer",
                            COHORT_ID
                    )
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("STOPPED"))
                    .andExpect(jsonPath("$.timerRunId").value(nullValue()))
                    .andExpect(jsonPath("$.startedAt").value(nullValue()))
                    .andExpect(jsonPath("$.elapsedSeconds").value(0L));

            verify(timerQueryService).getCurrent(USER_ID, COHORT_ID);
        }
    }

    @Nested
    @DisplayName("폐기")
    class Discard {

        @Test
        @DisplayName("정상 처리")
        void discardsTimer() throws Exception {
            mockMvc.perform(post(
                            "/api/v1/cohorts/{cohortId}/timer/{timerRunId}/discard",
                            COHORT_ID,
                            TIMER_RUN_ID
                    )
                            .header("X-User-Id", USER_ID)
                            .header("X-Command-Id", COMMAND_ID))
                    .andExpect(status().isNoContent());

            verify(timerCommandService).discard(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );
        }
    }

    @Nested
    @DisplayName("정상 종료")
    class Stop {

        @Test
        @DisplayName("미구현 응답 유지")
        void keepsStopEndpointUnimplemented() throws Exception {
            mockMvc.perform(post(
                            "/api/v1/cohorts/{cohortId}/timer/{timerRunId}/stop",
                            COHORT_ID,
                            TIMER_RUN_ID
                    )
                            .header("X-User-Id", USER_ID)
                            .header("X-Command-Id", COMMAND_ID))
                    .andExpect(status().isNotImplemented());

            verifyNoInteractions(timerCommandService, timerQueryService);
        }
    }
}

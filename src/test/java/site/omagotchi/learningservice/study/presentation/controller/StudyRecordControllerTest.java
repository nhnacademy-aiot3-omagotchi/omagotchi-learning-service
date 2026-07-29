package site.omagotchi.learningservice.study.presentation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.study.application.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.StudyRecordQueryService;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.result.DailyStudyRecordsResult;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.MonthlyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("학습 기록 API")
@ExtendWith(MockitoExtension.class)
class StudyRecordControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final Long COHORT_ID = 10L;
    private static final Long EXPECTED_VERSION = 1L;
    private static final LocalDate DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final LocalTime START_TIME = LocalTime.of(10, 0);
    private static final LocalTime END_TIME = LocalTime.of(11, 0);

    @Mock
    private StudyRecordCommandService studyRecordCommandService;

    @Mock
    private StudyRecordQueryService studyRecordQueryService;

    @InjectMocks
    private StudyRecordController studyRecordController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(studyRecordController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("단건 조회")
    class Get {

        @Test
        @DisplayName("정상 처리")
        void returnsStudyRecord() {
            StudyRecordResult result = studyRecordResult();
            given(studyRecordQueryService.getRecord(USER_ID, COHORT_ID, STUDY_RECORD_ID))
                    .willReturn(result);

            ResponseEntity<StudyRecordResponse> response = studyRecordController.get(
                    authentication(),
                    COHORT_ID,
                    STUDY_RECORD_ID
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(StudyRecordResponse.from(result), response.getBody());
        }
    }

    @Nested
    @DisplayName("일간 조회")
    class GetDailyRecords {

        @Test
        @DisplayName("정상 처리")
        void returnsDailyRecords() throws Exception {
            LocalDate aggregationDate = LocalDate.of(2000, Month.JANUARY, 1);
            DailyStudyRecordsResult result = new DailyStudyRecordsResult(
                    aggregationDate,
                    3_600L,
                    List.of(studyRecordResult())
            );
            given(studyRecordQueryService.getDailyRecords(
                    USER_ID,
                    COHORT_ID,
                    aggregationDate
            )).willReturn(result);

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-records",
                            COHORT_ID
                    )
                    .principal(authentication())
                    .queryParam("date", "2000-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-01"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.records[0].id").value(STUDY_RECORD_ID.toString()))
                    .andExpect(jsonPath("$.periodStart").doesNotExist())
                    .andExpect(jsonPath("$.periodEndExclusive").doesNotExist());

            verify(studyRecordQueryService).getDailyRecords(
                    USER_ID,
                    COHORT_ID,
                    aggregationDate
            );
        }

        @Test
        @DisplayName("잘못된 집계일 형식 예외")
        void rejectsInvalidAggregationDateFormat() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-records",
                            COHORT_ID
                    )
                    .principal(authentication())
                    .queryParam("date", "2000-02-30"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRecordQueryService);
        }

        @Test
        @DisplayName("집계일 누락 예외")
        void rejectsMissingAggregationDate() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-records",
                            COHORT_ID
                    )
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRecordQueryService);
        }
    }

    @Nested
    @DisplayName("월간 조회")
    class GetMonthlyStudySeconds {

        @Test
        @DisplayName("정상 처리")
        void returnsMonthlyStudySeconds() throws Exception {
            YearMonth aggregationMonth = YearMonth.of(2000, Month.JANUARY);
            MonthlyStudySecondsResult result = new MonthlyStudySecondsResult(
                    aggregationMonth,
                    3_600L,
                    List.of(
                            new DailyStudySecondsResult(
                                    LocalDate.of(2000, Month.JANUARY, 1),
                                    3_600L
                            ),
                            new DailyStudySecondsResult(
                                    LocalDate.of(2000, Month.JANUARY, 2),
                                    0L
                            )
                    )
            );
            given(studyRecordQueryService.getMonthlyStudySeconds(
                    USER_ID,
                    COHORT_ID,
                    aggregationMonth
            )).willReturn(result);

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-time-summaries",
                            COHORT_ID
                    )
                    .principal(authentication())
                    .queryParam("month", "2000-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationMonth").value("2000-01"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.dailyTotals[0].aggregationDate")
                            .value("2000-01-01"))
                    .andExpect(jsonPath("$.dailyTotals[1].studySeconds").value(0L))
                    .andExpect(jsonPath("$.periodStart").doesNotExist())
                    .andExpect(jsonPath("$.periodEndExclusive").doesNotExist());

            verify(studyRecordQueryService).getMonthlyStudySeconds(
                    USER_ID,
                    COHORT_ID,
                    aggregationMonth
            );
        }

        @Test
        @DisplayName("잘못된 집계월 형식 예외")
        void rejectsInvalidAggregationMonthFormat() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-time-summaries",
                            COHORT_ID
                    )
                    .principal(authentication())
                    .queryParam("month", "2000-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRecordQueryService);
        }

        @Test
        @DisplayName("집계월 누락 예외")
        void rejectsMissingAggregationMonth() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-time-summaries",
                            COHORT_ID
                    )
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRecordQueryService);
        }
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 처리")
        void createsStudyRecord() {
            CreateStudyRecordRequest request = new CreateStudyRecordRequest(
                    DATE,
                    START_TIME,
                    END_TIME
            );
            CreateStudyRecordCommand command = request.toCommand();
            StudyRecordResult result = studyRecordResult();
            given(studyRecordCommandService.create(COMMAND_ID, USER_ID, COHORT_ID, command))
                    .willReturn(result);

            ResponseEntity<StudyRecordResponse> response = studyRecordController.create(
                    authentication(),
                    COMMAND_ID,
                    COHORT_ID,
                    request
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(StudyRecordResponse.from(result), response.getBody());
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("정상 처리")
        void updatesStudyRecord() {
            UpdateStudyRecordRequest request = new UpdateStudyRecordRequest(
                    DATE,
                    START_TIME,
                    END_TIME,
                    EXPECTED_VERSION
            );
            UpdateStudyRecordCommand command = request.toCommand();
            StudyRecordResult result = studyRecordResult();
            given(studyRecordCommandService.update(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID,
                    command
            )).willReturn(result);

            ResponseEntity<StudyRecordResponse> response = studyRecordController.update(
                    authentication(),
                    COMMAND_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID,
                    request
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(StudyRecordResponse.from(result), response.getBody());
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("정상 처리")
        void deletesStudyRecord() {
            ResponseEntity<Void> response = studyRecordController.delete(
                    authentication(),
                    COMMAND_ID,
                    EXPECTED_VERSION,
                    COHORT_ID,
                    STUDY_RECORD_ID
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            assertNull(response.getBody());
            verify(studyRecordCommandService).delete(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    STUDY_RECORD_ID,
                    EXPECTED_VERSION
            );
        }
    }

    private StudyRecordResult studyRecordResult() {
        return new StudyRecordResult(
                STUDY_RECORD_ID,
                LocalDate.of(2000, Month.JANUARY, 1),
                Instant.parse("2000-01-01T01:00:00Z"),
                Instant.parse("2000-01-01T02:00:00Z"),
                3_600L,
                EXPECTED_VERSION,
                Instant.parse("2000-01-01T02:00:01Z"),
                Instant.parse("2000-01-01T02:00:01Z")
        );
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .claim("role", "USER")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}

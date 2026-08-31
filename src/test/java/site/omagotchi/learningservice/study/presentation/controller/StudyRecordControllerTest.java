package site.omagotchi.learningservice.study.presentation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.MediaType;
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

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("학습 기록 API")
@ExtendWith(MockitoExtension.class)
class StudyRecordControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
    );
    private static final Long COHORT_ID = 10L;
    private static final Long EXPECTED_VERSION = 1L;

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
    @DisplayName("기록 단건 조회")
    class Get {

        @Test
        @DisplayName("정상 처리")
        void returnsStudyRecord() throws Exception {
            StudyRecordResult result = studyRecordResult();
            given(studyRecordQueryService.getRecord(USER_ID, COHORT_ID, STUDY_RECORD_ID))
                    .willReturn(result);

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohort-id}/study-records/{study-record-id}",
                            COHORT_ID,
                            STUDY_RECORD_ID
                    )
                            .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(STUDY_RECORD_ID.toString()));
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
                            "/api/v1/cohorts/{cohort-id}/study-records",
                            COHORT_ID
                    )
                    .principal(authentication())
                    .queryParam("date", "2000-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-01"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.records[0].id").value(STUDY_RECORD_ID.toString()))
                    .andExpect(jsonPath("$.startTime").doesNotExist())
                    .andExpect(jsonPath("$.endTime").doesNotExist());

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
                            "/api/v1/cohorts/{cohort-id}/study-records",
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
                            "/api/v1/cohorts/{cohort-id}/study-records",
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
    @DisplayName("월간 요약 조회")
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
                            "/api/v1/cohorts/{cohort-id}/study-time-summaries",
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
                    .andExpect(jsonPath("$.startTime").doesNotExist())
                    .andExpect(jsonPath("$.endTime").doesNotExist());

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
                            "/api/v1/cohorts/{cohort-id}/study-time-summaries",
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
                            "/api/v1/cohorts/{cohort-id}/study-time-summaries",
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
        void createsStudyRecord() throws Exception {
            StudyRecordResult result = studyRecordResult();
            given(studyRecordCommandService.create(any(), any(), any()))
                    .willReturn(result);

            mockMvc.perform(post(
                            "/api/v1/cohorts/{cohort-id}/study-records",
                            COHORT_ID
                    )
                            .principal(authentication())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "startDateTime": "2000-01-01T23:30",
                                        "endDateTime": "2000-01-02T00:30"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(STUDY_RECORD_ID.toString()));

            ArgumentCaptor<CreateStudyRecordCommand> captor = ArgumentCaptor.forClass(CreateStudyRecordCommand.class);
            verify(studyRecordCommandService).create(eq(USER_ID), eq(COHORT_ID), captor.capture());
            CreateStudyRecordCommand command = captor.getValue();
            assertEquals(Instant.parse("2000-01-01T14:30:00Z"), command.startTime());
            assertEquals(Instant.parse("2000-01-01T15:30:00Z"), command.endTime());
        }

        @Test
        @DisplayName("초 단위 시간 형식 예외")
        void rejectsSecondPrecisionTime() throws Exception {
            mockMvc.perform(post(
                            "/api/v1/cohorts/{cohort-id}/study-records",
                            COHORT_ID
                    )
                            .principal(authentication())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "startDateTime": "2000-01-01T10:00:59",
                                        "endDateTime": "2000-01-01T11:00:01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.MALFORMED_REQUEST.code()));

            verifyNoInteractions(studyRecordCommandService);
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("정상 처리")
        void updatesStudyRecord() throws Exception {
            StudyRecordResult result = studyRecordResult();
            given(studyRecordCommandService.update(
                    eq(USER_ID),
                    eq(COHORT_ID),
                    eq(STUDY_RECORD_ID),
                    any()
            )).willReturn(result);

            mockMvc.perform(put(
                            "/api/v1/cohorts/{cohort-id}/study-records/{study-record-id}",
                            COHORT_ID,
                            STUDY_RECORD_ID
                    )
                            .principal(authentication())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "startDateTime": "2000-01-01T23:40",
                                        "endDateTime": "2000-01-02T00:40",
                                        "expectedVersion": 1
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(STUDY_RECORD_ID.toString()));

            ArgumentCaptor<UpdateStudyRecordCommand> captor = ArgumentCaptor.forClass(UpdateStudyRecordCommand.class);
            verify(studyRecordCommandService).update(
                    eq(USER_ID),
                    eq(COHORT_ID),
                    eq(STUDY_RECORD_ID),
                    captor.capture()
            );
            UpdateStudyRecordCommand command = captor.getValue();
            assertEquals(Instant.parse("2000-01-01T14:40:00Z"), command.startTime());
            assertEquals(Instant.parse("2000-01-01T15:40:00Z"), command.endTime());
            assertEquals(EXPECTED_VERSION, command.expectedVersion());
        }

        @Test
        @DisplayName("소수 초 시간 형식 예외")
        void rejectsFractionalSecondTime() throws Exception {
            mockMvc.perform(put(
                            "/api/v1/cohorts/{cohort-id}/study-records/{study-record-id}",
                            COHORT_ID,
                            STUDY_RECORD_ID
                    )
                            .principal(authentication())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "startDateTime": "2000-01-01T10:00:00.999",
                                        "endDateTime": "2000-01-01T11:00:00.001",
                                        "expectedVersion": 1
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.MALFORMED_REQUEST.code()));

            verifyNoInteractions(studyRecordCommandService);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("정상 처리")
        void deletesStudyRecord() throws Exception {
            mockMvc.perform(delete(
                            "/api/v1/cohorts/{cohort-id}/study-records/{study-record-id}",
                            COHORT_ID,
                            STUDY_RECORD_ID
                    )
                            .principal(authentication())
                            .header("X-RESOURCE-VERSION", EXPECTED_VERSION))
                    .andExpect(status().isNoContent());

            verify(studyRecordCommandService).delete(
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

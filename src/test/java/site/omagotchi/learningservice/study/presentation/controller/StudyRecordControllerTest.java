package site.omagotchi.learningservice.study.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.study.application.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.StudyRecordQueryService;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.result.DailyStudyRecordsResult;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.MonthlyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@DisplayName("학습 기록 API")
@WebMvcTest(controllers = StudyRecordController.class)
@LearningRestDocsTest
class StudyRecordControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
    );
    private static final Long COHORT_ID = 10L;
    private static final Long EXPECTED_VERSION = 1L;

    @MockitoBean
    private StudyRecordCommandService studyRecordCommandService;

    @MockitoBean
    private StudyRecordQueryService studyRecordQueryService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    private static String bearerToken() {
        return "Bearer "
                + TestJwtKeyConfig.issue(
                        "https://identity.omagotchi.local",
                        "omagotchi-api",
                        USER_ID.toString(),
                        "USER");
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
                                    STUDY_RECORD_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(STUDY_RECORD_ID.toString()))
                    .andDo(document(
                            "study-records/get",
                            pathParameters(cohortId(), studyRecordId()),
                            responseFields(studyRecordFields())));
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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-records", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .queryParam("date", "2000-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-01"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.records[0].id").value(STUDY_RECORD_ID.toString()))
                    .andExpect(jsonPath("$.startTime").doesNotExist())
                    .andExpect(jsonPath("$.endTime").doesNotExist())
                    .andDo(document(
                            "study-records/get-daily",
                            pathParameters(cohortId()),
                            queryParameters(
                                    parameterWithName("date")
                                            .description("집계할 날짜. `yyyy-MM-dd` 형식의 KST 날짜입니다.")),
                            responseFields(dailyRecordsFields())));

            verify(studyRecordQueryService).getDailyRecords(USER_ID, COHORT_ID, aggregationDate);
        }

        @Test
        @DisplayName("잘못된 집계일 형식 예외")
        void rejectsInvalidAggregationDateFormat() throws Exception {
            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-records", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .queryParam("date", "2000-02-30"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()))
                    .andDo(document(
                            "study-records/get-daily-invalid-date",
                            responseFields(errorFields())));

            verifyNoInteractions(studyRecordQueryService);
        }

        @Test
        @DisplayName("집계일 누락 예외")
        void rejectsMissingAggregationDate() throws Exception {
            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-records", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()))
                    .andDo(document(
                            "study-records/get-daily-missing-date",
                            responseFields(errorFields())));

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
                            new DailyStudySecondsResult(LocalDate.of(2000, Month.JANUARY, 2), 0L)
                    )
            );
            given(studyRecordQueryService.getMonthlyStudySeconds(
                    USER_ID,
                    COHORT_ID,
                    aggregationMonth
            )).willReturn(result);

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-time-summaries", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .queryParam("month", "2000-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationMonth").value("2000-01"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.dailyTotals[0].aggregationDate").value("2000-01-01"))
                    .andExpect(jsonPath("$.dailyTotals[1].studySeconds").value(0L))
                    .andExpect(jsonPath("$.startTime").doesNotExist())
                    .andExpect(jsonPath("$.endTime").doesNotExist())
                    .andDo(document(
                            "study-records/get-monthly-summary",
                            pathParameters(cohortId()),
                            queryParameters(parameterWithName("month").description("집계할 월. `yyyy-MM` 형식입니다.")),
                            responseFields(monthlySecondsFields())));

            verify(studyRecordQueryService).getMonthlyStudySeconds(
                    USER_ID,
                    COHORT_ID,
                    aggregationMonth
            );
        }

        @Test
        @DisplayName("잘못된 집계월 형식 예외")
        void rejectsInvalidAggregationMonthFormat() throws Exception {
            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-time-summaries", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .queryParam("month", "2000-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()))
                    .andDo(document(
                            "study-records/get-monthly-summary-invalid-month",
                            responseFields(errorFields())));

            verifyNoInteractions(studyRecordQueryService);
        }

        @Test
        @DisplayName("집계월 누락 예외")
        void rejectsMissingAggregationMonth() throws Exception {
            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-time-summaries", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()))
                    .andDo(document(
                            "study-records/get-monthly-summary-missing-month",
                            responseFields(errorFields())));

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

            mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/study-records", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                            {
                                "startDateTime": "2000-01-01T23:30",
                                "endDateTime": "2000-01-02T00:30"
                            }
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(STUDY_RECORD_ID.toString()))
                    .andDo(document(
                            "study-records/create",
                            pathParameters(cohortId()),
                            requestFields(createRequestFields()),
                            responseFields(studyRecordFields())));

            ArgumentCaptor<CreateStudyRecordCommand> captor = ArgumentCaptor.forClass(CreateStudyRecordCommand.class);
            verify(studyRecordCommandService).create(eq(USER_ID), eq(COHORT_ID), captor.capture());
            CreateStudyRecordCommand command = captor.getValue();
            assertEquals(Instant.parse("2000-01-01T14:30:00Z"), command.startTime());
            assertEquals(Instant.parse("2000-01-01T15:30:00Z"), command.endTime());
        }

        @Test
        @DisplayName("초 단위 시간 형식 예외")
        void rejectsSecondPrecisionTime() throws Exception {
            mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/study-records", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                            {
                                "startDateTime": "2000-01-01T10:00:59",
                                "endDateTime": "2000-01-01T11:00:01"
                            }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.MALFORMED_REQUEST.code()))
                    .andDo(document(
                            "study-records/create-invalid-time",
                            responseFields(errorFields())));

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
                                    STUDY_RECORD_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                            {
                                "startDateTime": "2000-01-01T23:40",
                                "endDateTime": "2000-01-02T00:40",
                                "expectedVersion": 1
                            }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(STUDY_RECORD_ID.toString()))
                    .andDo(document(
                            "study-records/update",
                            pathParameters(cohortId(), studyRecordId()),
                            requestFields(updateRequestFields()),
                            responseFields(studyRecordFields())));

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
                                    STUDY_RECORD_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                            {
                                "startDateTime": "2000-01-01T10:00:00.999",
                                "endDateTime": "2000-01-01T11:00:00.001",
                                "expectedVersion": 1
                            }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.MALFORMED_REQUEST.code()))
                    .andDo(document(
                            "study-records/update-invalid-time",
                            responseFields(errorFields())));

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
                                    STUDY_RECORD_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken())
                            .header("X-RESOURCE-VERSION", EXPECTED_VERSION))
                    .andExpect(status().isNoContent())
                    .andDo(document(
                            "study-records/delete",
                            pathParameters(cohortId(), studyRecordId()),
                            requestHeaders(
                                    headerWithName("X-RESOURCE-VERSION")
                                            .description("삭제 대상 기록의 최신 버전. 낙관적 동시성 제어에 사용합니다."))));

            verify(studyRecordCommandService)
                    .delete(USER_ID, COHORT_ID, STUDY_RECORD_ID, EXPECTED_VERSION);
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

    private static FieldDescriptor[] studyRecordFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("학습 기록 식별자"),
            fieldWithPath("aggregationDate").description("KST 기준 집계 날짜 (`yyyy-MM-dd`)"),
            fieldWithPath("startTime").description("학습 시작 시각 (UTC ISO-8601)"),
            fieldWithPath("endTime").description("학습 종료 시각 (UTC ISO-8601)"),
            fieldWithPath("studySeconds").description("학습 시간(초)"),
            fieldWithPath("version").description("낙관적 동시성 제어용 리소스 버전"),
            fieldWithPath("createdAt").description("생성 시각 (UTC ISO-8601)"),
            fieldWithPath("updatedAt").description("수정 시각 (UTC ISO-8601)")
        };
    }

    private static FieldDescriptor[] dailyRecordsFields() {
        return new FieldDescriptor[] {
            fieldWithPath("aggregationDate").description("KST 기준 집계 날짜 (`yyyy-MM-dd`)"),
            fieldWithPath("totalStudySeconds").description("해당 날짜의 총 학습 시간(초)"),
            fieldWithPath("records").description("해당 날짜의 학습 기록 목록"),
            fieldWithPath("records[].id").description("학습 기록 식별자"),
            fieldWithPath("records[].aggregationDate").description("KST 기준 집계 날짜"),
            fieldWithPath("records[].startTime").description("학습 시작 시각 (UTC ISO-8601)"),
            fieldWithPath("records[].endTime").description("학습 종료 시각 (UTC ISO-8601)"),
            fieldWithPath("records[].studySeconds").description("학습 시간(초)"),
            fieldWithPath("records[].version").description("리소스 버전"),
            fieldWithPath("records[].createdAt").description("생성 시각 (UTC ISO-8601)"),
            fieldWithPath("records[].updatedAt").description("수정 시각 (UTC ISO-8601)")
        };
    }

    private static FieldDescriptor[] monthlySecondsFields() {
        return new FieldDescriptor[] {
            fieldWithPath("aggregationMonth").description("집계 월 (`yyyy-MM`)"),
            fieldWithPath("totalStudySeconds").description("해당 월의 총 학습 시간(초)"),
            fieldWithPath("dailyTotals").description("일자별 학습 시간 목록"),
            fieldWithPath("dailyTotals[].aggregationDate").description("KST 기준 집계 날짜"),
            fieldWithPath("dailyTotals[].studySeconds").description("해당 날짜의 학습 시간(초)")
        };
    }

    private static FieldDescriptor[] createRequestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("startDateTime")
                    .description("KST 학습 시작 시각 (`yyyy-MM-dd'T'HH:mm`, 초·소수 초 제외)"),
            fieldWithPath("endDateTime")
                    .description("KST 학습 종료 시각 (`yyyy-MM-dd'T'HH:mm`, 초·소수 초 제외)")
        };
    }

    private static FieldDescriptor[] updateRequestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("startDateTime")
                    .description("KST 학습 시작 시각 (`yyyy-MM-dd'T'HH:mm`, 초·소수 초 제외)"),
            fieldWithPath("endDateTime")
                    .description("KST 학습 종료 시각 (`yyyy-MM-dd'T'HH:mm`, 초·소수 초 제외)"),
            fieldWithPath("expectedVersion").description("클라이언트가 마지막으로 조회한 리소스 버전")
        };
    }

    private static FieldDescriptor[] errorFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("오류 코드"),
            fieldWithPath("message").description("오류 메시지"),
            fieldWithPath("path").description("오류가 발생한 요청 경로"),
            fieldWithPath("requestId").description("요청 추적 식별자 (없으면 null)")
        };
    }

    private static ParameterDescriptor cohortId() {
        return parameterWithName("cohort-id").description("기수 식별자");
    }

    private static ParameterDescriptor studyRecordId() {
        return parameterWithName("study-record-id").description("학습 기록 식별자");
    }
}

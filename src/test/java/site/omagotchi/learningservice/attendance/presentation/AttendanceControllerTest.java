package site.omagotchi.learningservice.attendance.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.attendance.application.CurrentPresenceQueryService;
import site.omagotchi.learningservice.attendance.application.command.ChangeAttendanceStatusCommand;
import site.omagotchi.learningservice.attendance.application.query.AttendancePageQuery;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordPageResult;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.PresenceState;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = AttendanceController.class)
@DisplayName("나의 출결 API 계약")
@LearningRestDocsTest
class AttendanceControllerTest {

    private static final long COHORT_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private AttendanceService attendanceService;

    @MockitoBean
    private CurrentPresenceQueryService currentPresenceQueryService;

    @Test
    @DisplayName("체크인은 기존처럼 요청 본문 없이 출결을 기록한다")
    void checksInWithoutRequestBody() throws Exception {
        given(attendanceService.checkIn(COHORT_ID, USER_ID)).willReturn(record());

        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/attendance-records/check-in", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.spaceId").doesNotExist())
                .andDo(document(
                        "attendance/check-in",
                        pathParameters(cohortId()),
                        responseFields(recordFields())));

        verify(attendanceService).checkIn(COHORT_ID, USER_ID);
    }

    @Test
    @DisplayName("실습실 이동은 선택한 실습실 ID를 출결 서비스에 전달한다")
    void movesToSelectedLab() throws Exception {
        given(attendanceService.moveLab(COHORT_ID, USER_ID, 102L)).willReturn(record());

        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/attendance-records/move-lab", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType("application/json")
                        .content("{\"spaceId\":102}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.spaceId").value(102))
                .andDo(document(
                        "attendance/move-lab",
                        pathParameters(cohortId()),
                        requestFields(spaceRequestFields()),
                        responseFields(spaceMoveFields())));

        verify(attendanceService).moveLab(COHORT_ID, USER_ID, 102L);
    }

    @Test
    @DisplayName("도서관 입장은 선택한 공용 학습 공간 ID를 출결 서비스에 전달한다")
    void movesToSelectedStudySpace() throws Exception {
        given(attendanceService.moveStudySpace(COHORT_ID, USER_ID, 301L)).willReturn(record());

        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/attendance-records/move-study", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType("application/json")
                        .content("{\"spaceId\":301}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.spaceId").value(301))
                .andDo(document(
                        "attendance/move-study",
                        pathParameters(cohortId()),
                        requestFields(spaceRequestFields()),
                        responseFields(spaceMoveFields())));

        verify(attendanceService).moveStudySpace(COHORT_ID, USER_ID, 301L);
    }

    @Test
    @DisplayName("출결 체크아웃")
    void checksOut() throws Exception {
        // Given: 체크아웃 응답 준비
        given(attendanceService.checkOut(COHORT_ID, USER_ID)).willReturn(record());

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/attendance-records/check-out", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andDo(document(
                        "attendance/check-out",
                        pathParameters(cohortId()),
                        responseFields(recordFields())));

        verify(attendanceService).checkOut(COHORT_ID, USER_ID);
    }

    @Test
    @DisplayName("현재 위치 조회는 열린 체류구간의 공간과 상태를 반환한다")
    void getsCurrentPresence() throws Exception {
        given(currentPresenceQueryService.findCurrentPresence(COHORT_ID, USER_ID))
                .willReturn(Optional.of(new CurrentPresenceResult(
                        301L,
                        PresenceState.PRESENT,
                        Instant.parse("2026-09-02T01:00:00Z")
                )));

        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/attendance-records/current-presence",
                                COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId").value(301))
                .andExpect(jsonPath("$.state").value("PRESENT"))
                .andExpect(jsonPath("$.startedAt").value("2026-09-02T01:00:00Z"))
                .andDo(document(
                        "attendance/current-presence",
                        pathParameters(cohortId()),
                        responseFields(currentPresenceFields())));

        verify(currentPresenceQueryService).findCurrentPresence(COHORT_ID, USER_ID);
    }

    @Test
    @DisplayName("열린 체류구간이 없으면 현재 위치 조회는 본문 없이 성공한다")
    void returnsNoContentWhenCurrentPresenceDoesNotExist() throws Exception {
        given(currentPresenceQueryService.findCurrentPresence(COHORT_ID, USER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-records/current-presence", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("내 출결 목록은 JWT subject의 기록을 반환한다")
    void getsMyAttendanceRecords() throws Exception {
        AttendancePageQuery query = AttendancePageQuery.of(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                0,
                10
        );
        given(attendanceService.getMyRecords(COHORT_ID, USER_ID, query))
                .willReturn(new AttendanceRecordPageResult(List.of(record()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-records/me", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                // 본인 조회는 대상이 요청자 자신이라 내부 식별자를 내보내지 않는다.
                // 관리자 목록과 응답을 나눠 쓰는 이유가 이 단언이다.
                .andExpect(jsonPath("$.items[0].cohortMembershipId").doesNotExist())
                .andExpect(jsonPath("$.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.items[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.items[0].attendanceDate").value("2026-08-20"))
                .andExpect(jsonPath("$.items[0].autoStatus").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].finalStatus").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].checkedInAt").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andDo(document(
                        "attendance/get-my-records",
                        pathParameters(cohortId()),
                        responseFields(myPageFields())));

        verify(attendanceService).getMyRecords(COHORT_ID, USER_ID, query);
    }

    /**
     * 일자별 조회는 관리자용 엔드포인트다. date는 필수이고 page·size는 선택이며,
     * Controller가 date를 AttendancePageQuery의 from·to에 동시에 넣는다.
     * 이 바인딩이 깨지면 관리자 출결 화면이 조용히 다른 날짜를 보여준다.
     */
    @Test
    @DisplayName("일자별 출결 목록은 date와 페이지 조건을 그대로 전달한다")
    void getsDailyAttendanceRecords() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 20);
        AttendancePageQuery query = AttendancePageQuery.of(date, date, 0, 10);
        given(attendanceService.getDailyRecords(COHORT_ID, USER_ID, date, query))
                .willReturn(new AttendanceRecordPageResult(List.of(record()), 0, 10, 12, 2));

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-records", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .param("date", "2026-08-20")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].attendanceDate").value("2026-08-20"))
                .andExpect(jsonPath("$.items[0].finalStatus").value("PRESENT"))
                // 관리자 목록은 남의 기록을 여럿 그린다. 이 셋이 빠지면 화면은 행과
                // 구성원을 잇지 못하고 기록 번호밖에 표시하지 못한다.
                .andExpect(jsonPath("$.items[0].cohortMembershipId").value(20))
                .andExpect(
                        jsonPath("$.items[0].userId").value("00000000-0000-0000-0000-0000000000aa"))
                .andExpect(jsonPath("$.items[0].nickname").value("테스트닉"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(12))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andDo(document(
                        "attendance/get-daily-records",
                        pathParameters(cohortId()),
                        responseFields(adminPageFields())));

        verify(attendanceService).getDailyRecords(COHORT_ID, USER_ID, date, query);
    }

    /** page·size는 선택 값이다. 생략하면 AttendancePageQuery의 기본값(0, 20)이 적용된다. */
    @Test
    @DisplayName("일자별 출결 목록은 page와 size를 생략하면 기본값을 사용한다")
    void getsDailyAttendanceRecordsWithDefaultPaging() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 20);
        AttendancePageQuery query = AttendancePageQuery.of(date, date, null, null);
        given(attendanceService.getDailyRecords(COHORT_ID, USER_ID, date, query))
                .willReturn(new AttendanceRecordPageResult(List.of(record()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-records", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .param("date", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalPages").value(1));

        verify(attendanceService).getDailyRecords(COHORT_ID, USER_ID, date, query);
    }

    /** date는 필수다. 누락되면 Service를 호출하지 않고 400으로 끊어야 한다. */
    @Test
    @DisplayName("일자별 출결 목록은 date가 없으면 400을 반환한다")
    void rejectsDailyAttendanceRecordsWithoutDate() throws Exception {
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-records", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "attendance/get-daily-records-invalid",
                        pathParameters(cohortId()),
                        responseFields(errorFields())));

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("출결 최종 상태 변경")
    void changesFinalStatus() throws Exception {
        // Given: 상태 변경 요청과 응답 준비
        ChangeAttendanceStatusCommand command =
                new ChangeAttendanceStatusCommand(
                        AttendanceStatus.ABSENT, "수동 확인", "attendance-request-1");
        given(attendanceService.changeFinalStatus(COHORT_ID, 10L, USER_ID, command))
                .willReturn(record());

        // When & Then
        mockMvc.perform(patch(
                                "/api/v1/cohorts/{cohort-id}/attendance-records/{attendance-id}/status",
                                COHORT_ID,
                                10L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType("application/json")
                        .content(
                                "{\"nextStatus\":\"ABSENT\",\"reason\":\"수동 확인\",\"requestId\":\"attendance-request-1\"}"))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "attendance/change-final-status",
                        pathParameters(
                                cohortId(),
                                parameterWithName("attendance-id")
                                        .description("출결 기록 식별자")),
                        requestFields(statusRequestFields())));

        verify(attendanceService).changeFinalStatus(COHORT_ID, 10L, USER_ID, command);
    }

    private AttendanceRecordResult record() {
        return new AttendanceRecordResult(
                10L,
                20L,
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                "테스트닉",
                LocalDate.of(2026, 8, 20),
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T09:00:00Z"),
                0,
                0,
                0L,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T09:00:00Z")
        );
    }

    private static ParameterDescriptor cohortId() {
        return parameterWithName("cohort-id").description("기수 식별자");
    }

    private static FieldDescriptor[] spaceRequestFields() {
        return new FieldDescriptor[] {fieldWithPath("spaceId").description("이동할 공간 식별자")};
    }

    private static FieldDescriptor[] statusRequestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("nextStatus").description("변경할 최종 출결 상태"),
            fieldWithPath("reason").description("상태 변경 사유"),
            fieldWithPath("requestId").description("상태 변경 요청 식별자")
        };
    }

    private static FieldDescriptor[] recordFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("출결 기록 식별자"),
            fieldWithPath("attendanceDate").description("출결 날짜"),
            fieldWithPath("autoStatus").description("자동 판정 상태"),
            fieldWithPath("finalStatus").description("최종 출결 상태"),
            fieldWithPath("checkedInAt").description("입실 시각"),
            fieldWithPath("checkedOutAt").description("퇴실 시각"),
            fieldWithPath("lateMinutes").description("지각 시간(분)"),
            fieldWithPath("earlyLeaveMinutes").description("조퇴 시간(분)"),
            fieldWithPath("version").description("낙관적 동시성 버전"),
            fieldWithPath("createdAt").description("생성 시각"),
            fieldWithPath("updatedAt").description("수정 시각")
        };
    }

    private static FieldDescriptor[] spaceMoveFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("출결 기록 식별자"),
            fieldWithPath("attendanceDate").description("출결 날짜"),
            fieldWithPath("autoStatus").description("자동 판정 상태"),
            fieldWithPath("finalStatus").description("최종 출결 상태"),
            fieldWithPath("checkedInAt").description("입실 시각"),
            fieldWithPath("checkedOutAt").description("퇴실 시각"),
            fieldWithPath("lateMinutes").description("지각 시간(분)"),
            fieldWithPath("earlyLeaveMinutes").description("조퇴 시간(분)"),
            fieldWithPath("version").description("낙관적 동시성 버전"),
            fieldWithPath("createdAt").description("생성 시각"),
            fieldWithPath("updatedAt").description("수정 시각"),
            fieldWithPath("spaceId").description("이동한 공간 식별자")
        };
    }

    private static FieldDescriptor[] currentPresenceFields() {
        return new FieldDescriptor[] {
            fieldWithPath("spaceId").description("현재 공간 식별자"),
            fieldWithPath("state").description("현재 재실 상태"),
            fieldWithPath("startedAt").description("현재 체류 시작 시각")
        };
    }

    private static FieldDescriptor[] myPageFields() {
        return new FieldDescriptor[] {
            fieldWithPath("items").description("내 출결 기록 목록"),
            fieldWithPath("items[].id").description("출결 기록 식별자"),
            fieldWithPath("items[].attendanceDate").description("출결 날짜"),
            fieldWithPath("items[].autoStatus").description("자동 판정 상태"),
            fieldWithPath("items[].finalStatus").description("최종 출결 상태"),
            fieldWithPath("items[].checkedInAt").description("입실 시각"),
            fieldWithPath("items[].checkedOutAt").description("퇴실 시각"),
            fieldWithPath("items[].lateMinutes").description("지각 시간"),
            fieldWithPath("items[].earlyLeaveMinutes").description("조퇴 시간"),
            fieldWithPath("items[].version").description("버전"),
            fieldWithPath("items[].createdAt").description("생성 시각"),
            fieldWithPath("items[].updatedAt").description("수정 시각"),
            fieldWithPath("page.number").description("현재 페이지 번호"),
            fieldWithPath("page.size").description("페이지 크기"),
            fieldWithPath("page.totalElements").description("전체 항목 수"),
            fieldWithPath("page.totalPages").description("전체 페이지 수")
        };
    }

    private static FieldDescriptor[] adminPageFields() {
        return new FieldDescriptor[] {
            fieldWithPath("items").description("기수 구성원의 출결 기록 목록"),
            fieldWithPath("items[].id").description("출결 기록 식별자"),
            fieldWithPath("items[].cohortMembershipId").description("기수 소속 식별자"),
            fieldWithPath("items[].userId").description("사용자 식별자"),
            fieldWithPath("items[].nickname").description("사용자 닉네임"),
            fieldWithPath("items[].attendanceDate").description("출결 날짜"),
            fieldWithPath("items[].autoStatus").description("자동 판정 상태"),
            fieldWithPath("items[].finalStatus").description("최종 출결 상태"),
            fieldWithPath("items[].checkedInAt").description("입실 시각"),
            fieldWithPath("items[].checkedOutAt").description("퇴실 시각"),
            fieldWithPath("items[].lateMinutes").description("지각 시간"),
            fieldWithPath("items[].earlyLeaveMinutes").description("조퇴 시간"),
            fieldWithPath("items[].version").description("버전"),
            fieldWithPath("items[].createdAt").description("생성 시각"),
            fieldWithPath("items[].updatedAt").description("수정 시각"),
            fieldWithPath("page.number").description("현재 페이지 번호"),
            fieldWithPath("page.size").description("페이지 크기"),
            fieldWithPath("page.totalElements").description("전체 항목 수"),
            fieldWithPath("page.totalPages").description("전체 페이지 수")
        };
    }

    private static FieldDescriptor[] errorFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("오류 코드"),
                    fieldWithPath("message").description("오류 메시지"),
            fieldWithPath("path").description("오류 요청 경로"),
                    fieldWithPath("requestId").description("요청 추적 식별자 (없으면 null)")
        };
    }
}

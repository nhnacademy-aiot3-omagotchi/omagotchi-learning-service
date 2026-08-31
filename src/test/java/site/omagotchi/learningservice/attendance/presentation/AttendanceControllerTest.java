package site.omagotchi.learningservice.attendance.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordPageResult;
import site.omagotchi.learningservice.attendance.application.query.AttendancePageQuery;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AttendanceController.class)
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@DisplayName("나의 출결 API 계약")
class AttendanceControllerTest {

    private static final long COHORT_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceService attendanceService;

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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].cohortMembershipId").doesNotExist())
                .andExpect(jsonPath("$.items[0].attendanceDate").value("2026-08-20"))
                .andExpect(jsonPath("$.items[0].autoStatus").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].finalStatus").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].checkedInAt").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andDo(document("attendance/get-my-records"));

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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .param("date", "2026-08-20")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].attendanceDate").value("2026-08-20"))
                .andExpect(jsonPath("$.items[0].finalStatus").value("PRESENT"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(12))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andDo(document("attendance/get-daily-records"));

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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    private AttendanceRecordResult record() {
        return new AttendanceRecordResult(
                10L,
                20L,
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
}

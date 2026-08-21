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

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/attendance-records/me", COHORT_ID)
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
                .andDo(document("attendance/get-my-records"));

        verify(attendanceService).getMyRecords(COHORT_ID, USER_ID, query);
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

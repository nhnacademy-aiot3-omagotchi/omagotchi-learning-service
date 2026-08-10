package site.omagotchi.learningservice.cohort.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortAuditLogService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.result.CohortAuditLogResponse;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.application.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CohortController.class)
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@DisplayName("기수 API")
class CohortControllerTest {

    private static final Long COHORT_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CohortService cohortService;

    @MockitoBean
    private JoinCodeService joinCodeService;

    @MockitoBean
    private CohortMembershipService membershipService;

    @MockitoBean
    private CohortManagerService managerService;

    @MockitoBean
    private CohortAttendancePolicyService attendancePolicyService;

    @MockitoBean
    private CohortAuditLogService auditLogService;

    @Test
    @DisplayName("출결 정책 조회 요청을 현재 사용자로 서비스에 위임한다")
    void getsAttendancePolicy() throws Exception {
        given(attendancePolicyService.getPolicy(COHORT_ID, USER_ID))
                .willReturn(policyResponse());

        mockMvc.perform(get("/api/cohorts/{cohortId}/attendance-policy", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(COHORT_ID))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.scheduledStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.scheduledEndTime").value("18:00:00"))
                .andExpect(jsonPath("$.absenceCutoffTime").value("10:00:00"))
                .andExpect(jsonPath("$.allowedAwayMinutes").value(30));

        verify(attendancePolicyService).getPolicy(COHORT_ID, USER_ID);
    }

    @Test
    @DisplayName("출결 정책 저장 요청을 현재 사용자와 요청 본문으로 서비스에 위임한다")
    void savesAttendancePolicy() throws Exception {
        given(attendancePolicyService.savePolicy(
                eq(COHORT_ID),
                eq(new SaveAttendancePolicyCommand(
                        "Asia/Seoul",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(10, 0),
                        30
                )),
                eq(USER_ID)
        )).willReturn(policyResponse());

        mockMvc.perform(put("/api/cohorts/{cohortId}/attendance-policy", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timezone": "Asia/Seoul",
                                  "scheduledStartTime": "09:00:00",
                                  "scheduledEndTime": "18:00:00",
                                  "absenceCutoffTime": "10:00:00",
                                  "allowedAwayMinutes": 30
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(COHORT_ID))
                .andExpect(jsonPath("$.allowedAwayMinutes").value(30));

        verify(attendancePolicyService).savePolicy(
                COHORT_ID,
                new SaveAttendancePolicyCommand(
                        "Asia/Seoul",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(10, 0),
                        30
                ),
                USER_ID
        );
    }

    @Test
    @DisplayName("감사 로그 조회 요청을 현재 사용자로 서비스에 위임한다")
    void getsAuditLogs() throws Exception {
        given(auditLogService.getAuditLogs(COHORT_ID, USER_ID))
                .willReturn(java.util.List.of(new CohortAuditLogResponse(
                        1L,
                        COHORT_ID,
                        USER_ID,
                        "COHORT_MEMBERSHIP",
                        10L,
                        "CHANGE_MEMBER_ROLE",
                        null,
                        null,
                        "운영자 변경",
                        "manual-001",
                        OffsetDateTime.parse("2026-08-10T09:00:00+09:00")
                )));

        mockMvc.perform(get("/api/cohorts/{cohortId}/audit-logs", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].cohortId").value(COHORT_ID))
                .andExpect(jsonPath("$[0].targetType").value("COHORT_MEMBERSHIP"))
                .andExpect(jsonPath("$[0].targetId").value(10))
                .andExpect(jsonPath("$[0].action").value("CHANGE_MEMBER_ROLE"))
                .andExpect(jsonPath("$[0].reason").value("운영자 변경"))
                .andExpect(jsonPath("$[0].requestId").value("manual-001"));

        verify(auditLogService).getAuditLogs(COHORT_ID, USER_ID);
    }

    private CohortAttendancePolicyResponse policyResponse() {
        return new CohortAttendancePolicyResponse(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                30,
                USER_ID,
                OffsetDateTime.parse("2026-08-10T09:00:00+09:00")
        );
    }
}

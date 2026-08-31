package site.omagotchi.learningservice.cohort.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.UserAccessContextService;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.application.command.AssignCohortManagerCommand;
import site.omagotchi.learningservice.cohort.application.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.cohort.application.result.CohortAccessSummary;
import site.omagotchi.learningservice.cohort.application.result.UserAccessContextResult;
import site.omagotchi.learningservice.cohort.application.result.UserAccessType;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
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
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
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
    private UserAccessContextService userAccessContextService;

    @Test
    @DisplayName("내 접근 컨텍스트는 JWT 사용자와 전역 역할을 사용한다")
    void getsMyAccessContext() throws Exception {
        CohortAccessSummary managedCohort = new CohortAccessSummary(
                COHORT_ID,
                "AIoT 3기",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31),
                CohortStatus.PREPARING
        );
        given(userAccessContextService.getContext(USER_ID, GlobalRole.USER))
                .willReturn(new UserAccessContextResult(
                        GlobalRole.USER,
                        UserAccessType.COHORT_MANAGER,
                        List.of(managedCohort),
                        List.of()
                ));

        mockMvc.perform(get("/api/v1/cohorts/me/access-context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalRole").value("USER"))
                .andExpect(jsonPath("$.accessType").value("COHORT_MANAGER"))
                .andExpect(jsonPath("$.managedCohorts[0].cohortId").value(COHORT_ID))
                .andExpect(jsonPath("$.managedCohorts[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.studentCohorts").isEmpty())
                .andDo(document("cohort/get-my-access-context"));

        verify(userAccessContextService).getContext(USER_ID, GlobalRole.USER);
    }

    @Test
    @DisplayName("SYSTEM_ADMIN은 전체 기수 요약을 조회한다")
    void getsSystemAdminCohortSummaries() throws Exception {
        given(cohortService.getAdminSummaries(any())).willReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/cohorts/admin-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN")))
                .andExpect(status().isOk());

        verify(cohortService).getAdminSummaries(any());
    }

    @Test
    @DisplayName("SYSTEM_ADMIN의 PREPARING 기수 삭제는 204를 반환한다")
    void deletesPreparingCohort() throws Exception {
        mockMvc.perform(delete("/api/v1/cohorts/{cohort-id}", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN")))
                .andExpect(status().isNoContent());

        verify(cohortService).delete(eq(COHORT_ID), any());
    }

    @Test
    @DisplayName("운영 기간이 겹치는 관리자 배치는 409 계약 오류를 반환한다")
    void rejectsOverlappingManagerAssignment() throws Exception {
        UUID managerUserId = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");
        given(managerService.assignManager(
                eq(COHORT_ID),
                eq(new AssignCohortManagerCommand(managerUserId)),
                eq(USER_ID),
                any()
        )).willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_PERIOD_CONFLICT));

        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/managers", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"019d2a48-80c0-4d6a-9a15-0b16d2dd74f1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COHORT_MANAGER_PERIOD_CONFLICT"));
    }

    @Test
    @DisplayName("출결 정책 조회 요청을 현재 사용자로 서비스에 위임한다")
    void getsAttendancePolicy() throws Exception {
        given(attendancePolicyService.getPolicy(COHORT_ID, USER_ID))
                .willReturn(policyResponse());

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-policy", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(COHORT_ID))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.scheduledStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.scheduledEndTime").value("18:00:00"))
                .andExpect(jsonPath("$.absenceCutoffTime").value("10:00:00"))
                .andExpect(jsonPath("$.allowedAwayMinutes").value(30))
                .andDo(document("cohort/get-attendance-policy"));

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

        mockMvc.perform(put("/api/v1/cohorts/{cohort-id}/attendance-policy", COHORT_ID)
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

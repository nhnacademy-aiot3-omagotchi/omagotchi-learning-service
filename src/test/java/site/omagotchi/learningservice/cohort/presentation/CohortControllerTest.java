package site.omagotchi.learningservice.cohort.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.application.CohortManagerLookupService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.UserAccessContextService;
import site.omagotchi.learningservice.cohort.application.command.AssignCohortManagerCommand;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortMemberRoleCommand;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.application.result.CohortAccessSummary;
import site.omagotchi.learningservice.cohort.application.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.result.ManagedCohortResult;
import site.omagotchi.learningservice.cohort.application.result.UserAccessContextResult;
import site.omagotchi.learningservice.cohort.application.result.UserAccessType;
import site.omagotchi.learningservice.cohort.application.result.UserManagedCohortsResult;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = CohortController.class)
@DisplayName("기수 API")
@LearningRestDocsTest
class CohortControllerTest {

    private static final Long COHORT_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private CohortService cohortService;

    @MockitoBean
    private JoinCodeService joinCodeService;

    @MockitoBean
    private CohortMembershipService membershipService;

    @MockitoBean
    private CohortManagerService managerService;

    @MockitoBean
    private CohortManagerLookupService managerLookupService;

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
                .andExpect(jsonPath("$.studentCohorts").isEmpty());

        verify(userAccessContextService).getContext(USER_ID, GlobalRole.USER);
    }

    @Test
    @DisplayName("SYSTEM_ADMIN은 전체 기수 요약을 조회한다")
    void getsSystemAdminCohortSummaries() throws Exception {
        given(cohortService.getAdminSummaries(any())).willReturn(List.of());

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
        given(attendancePolicyService.getPolicy(COHORT_ID, USER_ID, GlobalRole.USER))
                .willReturn(policyResponse());

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/attendance-policy", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(COHORT_ID))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.scheduledStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.scheduledEndTime").value("18:00:00"))
                .andExpect(jsonPath("$.absenceCutoffTime").value("10:00:00"))
                .andExpect(jsonPath("$.allowedAwayMinutes").value(30))
                .andDo(document(
                        "cohort/get-attendance-policy",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        responseFields(policyResponseFields())));

        verify(attendancePolicyService).getPolicy(COHORT_ID, USER_ID, GlobalRole.USER);
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
                eq(USER_ID),
                eq(GlobalRole.USER)
        )).willReturn(policyResponse());

        mockMvc.perform(put("/api/v1/cohorts/{cohort-id}/attendance-policy", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
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
                .andExpect(jsonPath("$.allowedAwayMinutes").value(30))
                .andDo(document(
                        "cohort-management/save-attendance-policy",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        requestFields(policyRequestFields()),
                        responseFields(policyResponseFields())));

        verify(attendancePolicyService).savePolicy(
                COHORT_ID,
                new SaveAttendancePolicyCommand(
                        "Asia/Seoul",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(10, 0),
                        30
                ),
                USER_ID,
                GlobalRole.USER
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

    @Test
    @DisplayName("SYSTEM_ADMIN은 사용자별 기수 운영 권한을 일괄 조회한다")
    void searchesManagedCohortsForSystemAdmin() throws Exception {
        given(managerLookupService.findManagedCohorts(any(), eq(GlobalRole.SYSTEM_ADMIN)))
                .willReturn(List.of(new UserManagedCohortsResult(
                        USER_ID,
                        List.of(new ManagedCohortResult(
                                COHORT_ID, "1기", CohortMembershipRole.MANAGER))
                )));

        mockMvc.perform(post("/api/v1/cohorts/managers/search")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds": ["%s"]}
                                """.formatted(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$[0].cohorts[0].cohortName").value("1기"))
                .andExpect(jsonPath("$[0].cohorts[0].role").value("MANAGER"));
    }

    @Test
    @DisplayName("일반 사용자의 기수 운영 권한 조회는 403이다")
    void rejectsManagedCohortSearchForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/managers/search")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"userIds": ["%s"]}
                        """
                                        .formatted(USER_ID)))
                .andExpect(status().isForbidden())
                .andDo(document(
                        "cohort-management/search-managers-forbidden",
                        requestFields(
                                fieldWithPath("userIds").description("조회할 사용자 식별자 목록"),
                                fieldWithPath("userIds[]").description("사용자 식별자")),
                        responseFields(errorFields())));
    }

    @Test
    @DisplayName("빈 userIds 요청은 400이다")
    void rejectsEmptyUserIds() throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/managers/search")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"userIds": []}
                        """))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "cohort-management/search-managers-invalid",
                        requestFields(fieldWithPath("userIds").description("비어 있는 사용자 식별자 목록")),
                        responseFields(errorFields())));
    }

    @Test
    @DisplayName("활성 기수원 목록 조회")
    void getsMembers() throws Exception {
        // Given: 기수원 목록 응답 준비
        given(membershipService.getMembers(COHORT_ID, USER_ID))
                .willReturn(List.of(memberResponse()));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/members", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nickname").value("테스트사용자"))
                .andDo(document(
                        "cohort-management/get-members",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        responseFields(memberListFields())));
    }

    @Test
    @DisplayName("사용자별 관리 기수 조회")
    void searchesManagedCohortsWithDocumentation() throws Exception {
        // Given: 관리 기수 조회 응답 준비
        given(managerLookupService.findManagedCohorts(any(), eq(GlobalRole.SYSTEM_ADMIN)))
                .willReturn(
                        List.of(
                                new UserManagedCohortsResult(
                                        USER_ID,
                                        List.of(
                                                new ManagedCohortResult(
                                                        COHORT_ID,
                                                        "테스트 기수",
                                                        CohortMembershipRole.MANAGER)))));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/managers/search")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + USER_ID + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cohorts[0].role").value("MANAGER"))
                .andDo(document(
                        "cohort-management/search-managers",
                        requestFields(
                                fieldWithPath("userIds").description("조회할 사용자 식별자 목록"),
                                fieldWithPath("userIds[]").description("사용자 식별자")),
                        responseFields(managedCohortListFields())));
    }

    @Test
    @DisplayName("기수 관리자 지정")
    void assignsManager() throws Exception {
        // Given: 관리자 지정 요청과 응답 준비
        UUID managerId = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f2");
        given(
                        managerService.assignManager(
                                eq(COHORT_ID),
                                eq(new AssignCohortManagerCommand(managerId)),
                                eq(USER_ID),
                                eq(GlobalRole.SYSTEM_ADMIN)))
                .willReturn(memberResponse(managerId, CohortMembershipRole.MANAGER));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/managers", COHORT_ID)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + managerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(managerId.toString()))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andDo(document(
                        "cohort-management/assign-manager",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        requestFields(fieldWithPath("userId").description("관리자로 지정할 사용자 식별자")),
                        responseFields(memberFields())));
    }

    @Test
    @DisplayName("기수원 역할 변경")
    void changesMemberRole() throws Exception {
        // Given: 역할 변경 요청과 응답 준비
        UUID memberId = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f2");
        given(
                        managerService.changeMemberRole(
                                eq(COHORT_ID),
                                eq(memberId),
                                eq(new ChangeCohortMemberRoleCommand(CohortMembershipRole.MENTOR)),
                                eq(USER_ID),
                                eq(GlobalRole.SYSTEM_ADMIN)))
                .willReturn(memberResponse(memberId, CohortMembershipRole.MENTOR));

        // When & Then
        mockMvc.perform(patch(
                                "/api/v1/cohorts/{cohort-id}/members/{member-user-id}/role",
                                COHORT_ID,
                                memberId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MENTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(memberId.toString()))
                .andExpect(jsonPath("$.role").value("MENTOR"))
                .andDo(document(
                        "cohort-management/change-member-role",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 식별자"),
                                parameterWithName("member-user-id")
                                        .description("역할을 변경할 사용자 식별자")),
                        requestFields(fieldWithPath("role").description("변경할 기수 역할")),
                        responseFields(memberFields())));
    }

    private CohortMembershipResponse memberResponse() {
        return memberResponse(USER_ID, CohortMembershipRole.STUDENT);
    }

    private CohortMembershipResponse memberResponse(UUID userId, CohortMembershipRole role) {
        return new CohortMembershipResponse(
                10L,
                COHORT_ID,
                userId,
                role,
                CohortMembershipStatus.ACTIVE,
                OffsetDateTime.parse("2026-01-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-01-01T09:05:00+09:00"),
                USER_ID,
                null,
                null,
                "테스트사용자");
    }

    private static FieldDescriptor[] memberFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("소속 식별자"),
                    fieldWithPath("cohortId").description("기수 식별자"),
                    fieldWithPath("userId").description("사용자 식별자"),
            fieldWithPath("role").description("기수 역할"),
                    fieldWithPath("status").description("소속 상태"),
                    fieldWithPath("requestedAt").description("가입 요청 시각"),
            fieldWithPath("processedAt").description("처리 시각"),
                    fieldWithPath("processedByUserId").description("처리한 사용자 식별자"),
                    fieldWithPath("rejectionReason").description("거절 사유 (nullable)"),
            fieldWithPath("endedAt").description("소속 종료 시각 (nullable)"),
                    fieldWithPath("nickname").description("사용자 닉네임 (nullable)")
        };
    }

    private static FieldDescriptor[] memberListFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].id").description("소속 식별자"),
                    fieldWithPath("[].cohortId").description("기수 식별자"),
                    fieldWithPath("[].userId").description("사용자 식별자"),
            fieldWithPath("[].role").description("기수 역할"),
                    fieldWithPath("[].status").description("소속 상태"),
                    fieldWithPath("[].requestedAt").description("가입 요청 시각"),
            fieldWithPath("[].processedAt").description("처리 시각"),
                    fieldWithPath("[].processedByUserId").description("처리한 사용자 식별자"),
                    fieldWithPath("[].rejectionReason").description("거절 사유"),
            fieldWithPath("[].endedAt").description("소속 종료 시각"),
                    fieldWithPath("[].nickname").description("사용자 닉네임")
        };
    }

    private static FieldDescriptor[] managedCohortListFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].userId").description("사용자 식별자"),
            fieldWithPath("[].cohorts").description("사용자가 관리하는 기수 목록"),
            fieldWithPath("[].cohorts[].cohortId").description("기수 식별자"),
            fieldWithPath("[].cohorts[].cohortName").description("기수 이름"),
            fieldWithPath("[].cohorts[].role").description("관리 역할")
        };
    }

    private static FieldDescriptor[] policyRequestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("timezone").description("출결 기준 시간대"),
            fieldWithPath("scheduledStartTime").description("정규 시작 시각"),
            fieldWithPath("scheduledEndTime").description("정규 종료 시각"),
            fieldWithPath("absenceCutoffTime").description("결석 판정 시각 (nullable)"),
            fieldWithPath("allowedAwayMinutes").description("허용 자리비움 시간(분)")
        };
    }

    private static FieldDescriptor[] policyResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("cohortId").description("기수 식별자"),
                    fieldWithPath("timezone").description("출결 기준 시간대"),
                    fieldWithPath("scheduledStartTime").description("정규 시작 시각"),
            fieldWithPath("scheduledEndTime").description("정규 종료 시각"),
                    fieldWithPath("absenceCutoffTime").description("결석 판정 시각"),
                    fieldWithPath("allowedAwayMinutes").description("허용 자리비움 시간(분)"),
            fieldWithPath("updatedByUserId").description("수정한 사용자 식별자"),
                    fieldWithPath("updatedAt").description("수정 시각")
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

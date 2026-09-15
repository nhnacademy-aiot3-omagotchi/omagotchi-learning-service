package site.omagotchi.learningservice.cohort.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortManagerLookupService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.UserAccessContextService;
import site.omagotchi.learningservice.cohort.application.command.ApproveMembershipCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateJoinCommand;
import site.omagotchi.learningservice.cohort.application.command.IssueJoinCodeCommand;
import site.omagotchi.learningservice.cohort.application.command.RejectMembershipCommand;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.result.IssuedJoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.result.JoinCodeResponse;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = {CohortController.class, CohortMembershipController.class})
@DisplayName("기수 가입 API 계약")
@LearningRestDocsTest
class CohortJoiningDocumentationTest {

    private static final Long COHORT_ID = 1L;
    private static final Long MEMBERSHIP_ID = 20L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final OffsetDateTime ISSUED_AT = OffsetDateTime.parse("2026-09-14T09:00:00+09:00");
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2026-09-21T09:00:00+09:00");

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
    @DisplayName("가입 코드 조회")
    void getsJoinCode() throws Exception {
        // Given: 가입 코드 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(joinCodeService.getLatestJoinCode(COHORT_ID, USER_ID))
                .willReturn(joinCode(CohortJoinCodeStatus.ACTIVE));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/join-code", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andDo(document(
                        "cohort-joining/get-join-code",
                        pathParameters(cohortId()),
                        responseFields(joinCodeFields())));
    }

    @Test
    @DisplayName("가입 코드 발급")
    void issuesJoinCode() throws Exception {
        // Given: 가입 코드 발급에 필요한 요청과 서비스 응답을 준비한다.
        given(joinCodeService.issue(eq(COHORT_ID), any(IssueJoinCodeCommand.class), eq(USER_ID)))
                .willReturn(
                        new IssuedJoinCodeResponse(
                                COHORT_ID,
                                "ABCD2345",
                                CohortJoinCodeStatus.ACTIVE,
                                EXPIRES_AT,
                                ISSUED_AT));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/join-code", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"2026-09-21T09:00:00+09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ABCD2345"))
                .andDo(document(
                        "cohort-joining/issue-join-code",
                        pathParameters(cohortId()),
                        requestFields(fieldWithPath("expiresAt").description("가입 코드 만료 시각 (ISO-8601)")),
                        responseFields(issuedJoinCodeFields())));
    }

    @Test
    @DisplayName("가입 코드 폐기")
    void revokesJoinCode() throws Exception {
        // Given: 가입 코드 폐기에 필요한 요청과 서비스 응답을 준비한다.
        given(joinCodeService.revoke(COHORT_ID, USER_ID))
                .willReturn(joinCode(CohortJoinCodeStatus.REVOKED));

        // When & Then
        mockMvc.perform(patch("/api/v1/cohorts/{cohort-id}/join-code/revoke", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andDo(document(
                        "cohort-joining/revoke-join-code",
                        pathParameters(cohortId()),
                        responseFields(joinCodeFields())));
    }

    @Test
    @DisplayName("가입 요청으로 기수 가입")
    void joinsByJoinRequest() throws Exception {
        // Given: 가입 요청으로 기수 가입에 필요한 요청과 서비스 응답을 준비한다.
        given(membershipService.join(eq(new CreateJoinCommand("ABCD2345")), eq(USER_ID)))
                .willReturn(membership(CohortMembershipStatus.PENDING));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/join-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"ABCD2345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andDo(document(
                        "cohort-joining/create-join-request",
                        requestFields(fieldWithPath("joinCode").description("가입 코드 원문")),
                        responseFields(membershipFields())));
    }

    @Test
    @DisplayName("가입 신청")
    void appliesByApplication() throws Exception {
        // Given: 가입 신청에 필요한 요청과 서비스 응답을 준비한다.
        given(membershipService.join(eq(new CreateJoinCommand("ABCD2345")), eq(USER_ID)))
                .willReturn(membership(CohortMembershipStatus.PENDING));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD2345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andDo(document(
                        "cohort-joining/create-application",
                        requestFields(fieldWithPath("code").description("가입 코드 별칭")),
                        responseFields(membershipFields())));
    }

    @Test
    @DisplayName("내 가입 요청 조회")
    void getsMyJoinRequests() throws Exception {
        // Given: 내 가입 요청 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(membershipService.getMyMemberships(USER_ID))
                .willReturn(List.of(membership(CohortMembershipStatus.PENDING)));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/join-requests/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(MEMBERSHIP_ID))
                .andDo(document(
                        "cohort-joining/get-my-join-requests",
                        responseFields(membershipListFields())));
    }

    @Test
    @DisplayName("기수 가입 요청 조회")
    void getsPendingJoinRequests() throws Exception {
        // Given: 기수 가입 요청 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(membershipService.getPendingJoinRequests(COHORT_ID, USER_ID))
                .willReturn(List.of(membership(CohortMembershipStatus.PENDING)));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/join-requests", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andDo(document(
                        "cohort-joining/get-join-requests",
                        pathParameters(cohortId()),
                        responseFields(membershipListFields())));
    }

    @Test
    @DisplayName("가입 요청 승인")
    void approvesMembership() throws Exception {
        // Given: 가입 요청 승인에 필요한 요청과 서비스 응답을 준비한다.
        given(
                        membershipService.approve(
                                eq(MEMBERSHIP_ID),
                                eq(new ApproveMembershipCommand(CohortMembershipRole.STUDENT)),
                                eq(USER_ID),
                                eq(GlobalRole.SYSTEM_ADMIN)))
                .willReturn(membership(CohortMembershipStatus.ACTIVE));

        // When & Then
        mockMvc.perform(patch("/api/v1/cohort-memberships/{membership-id}/approve", MEMBERSHIP_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andDo(document(
                        "cohort-joining/approve-membership",
                        pathParameters(membershipId()),
                        requestFields(fieldWithPath("role").description("승인 후 부여할 역할")),
                        responseFields(membershipFields())));
    }

    @Test
    @DisplayName("가입 요청 거절")
    void rejectsMembership() throws Exception {
        // Given: 가입 요청 거절에 필요한 요청과 서비스 응답을 준비한다.
        given(
                        membershipService.reject(
                                eq(MEMBERSHIP_ID),
                                eq(new RejectMembershipCommand("기수 기간이 종료되었습니다.")),
                                eq(USER_ID)))
                .willReturn(membership(CohortMembershipStatus.REJECTED));

        // When & Then
        mockMvc.perform(patch("/api/v1/cohort-memberships/{membership-id}/reject", MEMBERSHIP_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"기수 기간이 종료되었습니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andDo(document(
                        "cohort-joining/reject-membership",
                        pathParameters(membershipId()),
                        requestFields(fieldWithPath("reason").description("가입 신청 거절 사유")),
                        responseFields(membershipFields())));
    }

    private String bearer() {
        return bearer("USER");
    }

    private String bearer(String role) {
        return "Bearer " + TestJwtKeyConfig.issue(role);
    }

    private JoinCodeResponse joinCode(CohortJoinCodeStatus status) {
        return new JoinCodeResponse(
                COHORT_ID,
                status,
                EXPIRES_AT,
                ISSUED_AT,
                status == CohortJoinCodeStatus.REVOKED ? ISSUED_AT.plusDays(1) : null);
    }

    private CohortMembershipResponse membership(CohortMembershipStatus status) {
        return new CohortMembershipResponse(
                MEMBERSHIP_ID,
                COHORT_ID,
                USER_ID,
                CohortMembershipRole.STUDENT,
                status,
                ISSUED_AT,
                status == CohortMembershipStatus.PENDING ? null : EXPIRES_AT,
                status == CohortMembershipStatus.PENDING ? null : USER_ID,
                status == CohortMembershipStatus.REJECTED ? "기수 기간이 종료되었습니다." : null,
                null,
                "문서사용자");
    }

    private static ParameterDescriptor cohortId() {
        return parameterWithName("cohort-id").description("기수 식별자");
    }

    private static ParameterDescriptor membershipId() {
        return parameterWithName("membership-id").description("기수 소속 또는 가입 신청 식별자");
    }

    private static FieldDescriptor[] joinCodeFields() {
        return new FieldDescriptor[] {
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("status").description("가입 코드 상태"),
            fieldWithPath("expiresAt").description("만료 시각"),
            fieldWithPath("issuedAt").description("발급 시각"),
            fieldWithPath("revokedAt").description("폐기 시각 (활성 코드는 null)")
        };
    }

    private static FieldDescriptor[] issuedJoinCodeFields() {
        return new FieldDescriptor[] {
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("code").description("가입 코드 원문. 발급 응답에서만 반환"),
            fieldWithPath("status").description("가입 코드 상태"),
            fieldWithPath("expiresAt").description("만료 시각"),
            fieldWithPath("issuedAt").description("발급 시각")
        };
    }

    private static FieldDescriptor[] membershipFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("소속 식별자"),
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("userId").description("사용자 식별자"),
            fieldWithPath("role").description("기수 역할"),
            fieldWithPath("status").description("소속 상태"),
            fieldWithPath("requestedAt").description("가입 신청 시각"),
            fieldWithPath("processedAt").description("처리 시각 (미처리 시 null)"),
            fieldWithPath("processedByUserId").description("처리한 사용자 식별자 (미처리 시 null)"),
            fieldWithPath("rejectionReason").description("거절 사유 (거절되지 않았으면 null)"),
            fieldWithPath("endedAt").description("소속 종료 시각 (종료되지 않았으면 null)"),
            fieldWithPath("nickname").description("사용자 닉네임")
        };
    }

    private static FieldDescriptor[] membershipListFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].id").description("소속 식별자"),
            fieldWithPath("[].cohortId").description("기수 식별자"),
            fieldWithPath("[].userId").description("사용자 식별자"),
            fieldWithPath("[].role").description("기수 역할"),
            fieldWithPath("[].status").description("소속 상태"),
            fieldWithPath("[].requestedAt").description("가입 신청 시각"),
            fieldWithPath("[].processedAt").description("처리 시각"),
            fieldWithPath("[].processedByUserId").description("처리한 사용자 식별자"),
            fieldWithPath("[].rejectionReason").description("거절 사유"),
            fieldWithPath("[].endedAt").description("소속 종료 시각"),
            fieldWithPath("[].nickname").description("사용자 닉네임")
        };
    }
}

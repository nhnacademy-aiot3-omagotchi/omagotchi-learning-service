package site.omagotchi.learningservice.team.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberCandidateQueryService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.result.TeamDetailResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateStatus;
import site.omagotchi.learningservice.team.application.result.TeamMemberResult;
import site.omagotchi.learningservice.team.application.result.TeamResult;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

/**
 * 팀 API의 요청자 식별.
 *
 * <p><b>요청자를 어디서 읽는지가 곧 권한이다.</b> 팀 API는 전부 "요청자가 이 팀의 MASTER인가"로 갈리므로, 요청자를 헤더로 받으면 헤더 한 줄로 남의 팀을
 * 해체할 수 있다. 그래서 이 컨트롤러는 Access JWT의 {@code sub}만 읽는다.
 *
 * <p>게이트웨이가 들어오는 {@code X-User-Id}를 제거하지만(default-filters) 그것에 기대지 않는다 — 게이트웨이를 우회하는 내부 호출 경로가 ADR
 * 0010으로 열려 있다.
 */
@WebMvcTest(controllers = TeamController.class)
@LearningRestDocsTest
class TeamControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPOOFED_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private TeamMemberService teamMemberService;

    @MockitoBean
    private TeamMasterService teamMasterService;

    @MockitoBean
    private TeamMemberCandidateQueryService teamMemberCandidateQueryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    private static String bearerToken() {
        return "Bearer "
                + TestJwtKeyConfig.issue(
                        TestJwtKeyConfig.ISSUER,
                        TestJwtKeyConfig.AUDIENCE,
                        USER_ID.toString(),
                        "USER");
    }

    @Test
    @DisplayName("요청자를 JWT의 주체에서 읽는다.")
    void readsRequesterFromJwtSubject() throws Exception {
        when(teamService.getMyTeams(USER_ID)).thenReturn(List.of(teamResult()));

        mockMvc.perform(get("/api/v1/teams/me").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andDo(document("teams/get-my-teams", responseFields(teamListFields())));

        verify(teamService).getMyTeams(USER_ID);
    }

    /**
     * 헤더로는 요청자를 바꿀 수 없어야 한다. 여기가 뚫리면 헤더 한 줄로 남의 팀에서
     * 마스터 행세를 할 수 있다.
     */
    @Test
    @DisplayName("위조된 X-User-Id 헤더가 있어도 JWT의 주체를 쓴다.")
    void usesJwtSubjectEvenWhenUserHeaderIsSpoofed() throws Exception {
        when(teamService.getMyTeams(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/teams/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("X-User-Id", SPOOFED_ID))
                .andExpect(status().isOk());

        verify(teamService).getMyTeams(USER_ID);
    }

    /**
     * 상태를 바꾸는 경로에서도 같아야 한다. 조회만 JWT를 쓰고 해체는 헤더를 쓰는 식으로
     * 갈리면 가장 위험한 쪽이 뚫린다.
     */
    @Test
    @DisplayName("팀 해체도 JWT의 주체로 수행한다.")
    void disbandUsesJwtSubject() throws Exception {
        mockMvc.perform(delete("/api/v1/teams/{team-id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("X-User-Id", SPOOFED_ID))
                .andExpect(status().isNoContent())
                .andDo(document("teams/disband", pathParameters(teamId())));

        verify(teamMasterService).disband(1L, USER_ID);
    }

    @Test
    @DisplayName("팀 생성 응답은 201이고 요청자는 JWT의 주체다.")
    void createReturnsCreatedWithJwtSubject() throws Exception {
        when(teamService.create(3L, "테스트 팀", USER_ID)).thenReturn(new TeamResult(
                1L, 3L, "테스트 팀",
                OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9))));

        mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cohortId\":3,\"name\":\"테스트 팀\"}"))
                .andExpect(status().isCreated())
                .andDo(document(
                        "teams/create",
                        requestFields(
                                fieldWithPath("cohortId")
                                                .optional().description("기수 식별자 (생략 가능)"),
                                fieldWithPath("name").description("팀 이름")),
                        responseFields(teamFields())));

        verify(teamService).create(3L, "테스트 팀", USER_ID);
    }

    @Test
    @DisplayName("팀원 후보 검색은 JWT 요청자를 전달하고 후보 상태를 반환한다.")
    void searchesMemberCandidatesWithJwtRequester() throws Exception {
        UUID candidateId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(teamMemberCandidateQueryService.search(1L, "학생", USER_ID)).thenReturn(List.of(
                new TeamMemberCandidateResult(
                        candidateId,
                        "학생 일",
                        "student@example.com",
                        TeamMemberCandidateStatus.AVAILABLE
                )
        ));

        mockMvc.perform(get("/api/v1/teams/{team-id}/member-candidates", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .queryParam("query", "학생"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andDo(document(
                        "teams/search-member-candidates",
                        pathParameters(teamId()),
                        queryParameters(parameterWithName("query").description("검색어")),
                        responseFields(candidateFields())));

        verify(teamMemberCandidateQueryService).search(1L, "학생", USER_ID);
    }

    @Test
    @DisplayName("팀 상세 조회")
    void getsTeamDetail() throws Exception {
        // Given: 팀 상세 응답 준비
        when(teamService.getTeam(1L, USER_ID))
                .thenReturn(
                        new TeamDetailResult(
                                1L,
                                3L,
                                "테스트 팀",
                                OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
                                10L,
                                TeamMemberRole.MASTER,
                                List.of(
                                        new TeamMemberResult(
                                                10L,
                                                "학생 일",
                                                TeamMemberRole.MASTER,
                                                OffsetDateTime.of(
                                                        2026,
                                                        7,
                                                        24,
                                                        10,
                                                        0,
                                                        0,
                                                        0,
                                                        ZoneOffset.ofHours(9))))));

        // When & Then
        mockMvc.perform(get("/api/v1/teams/{team-id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andDo(document(
                        "teams/get-team",
                        pathParameters(teamId()),
                        responseFields(detailFields())));
    }

    @Test
    @DisplayName("팀원 추가")
    void addsMember() throws Exception {
        // Given: 추가할 사용자와 인증 준비
        // When & Then
        mockMvc.perform(post("/api/v1/teams/{team-id}/members", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isCreated())
                .andDo(document(
                        "teams/add-member",
                        pathParameters(teamId()),
                        requestFields(fieldWithPath("targetUserId").description("추가할 사용자 식별자"))));
    }

    @Test
    @DisplayName("팀원 제외")
    void kicksMember() throws Exception {
        // Given: 제외할 팀원과 인증 준비
        // When & Then
        mockMvc.perform(delete("/api/v1/teams/{team-id}/members/{member-id}", 1L, 11L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent())
                .andDo(document("teams/kick-member", pathParameters(teamId(), memberId())));
    }

    @Test
    @DisplayName("팀 탈퇴")
    void leavesTeam() throws Exception {
        // Given: 탈퇴할 팀과 인증 준비
        // When & Then
        mockMvc.perform(post("/api/v1/teams/{team-id}/leave", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent())
                .andDo(document("teams/leave", pathParameters(teamId())));
    }

    @Test
    @DisplayName("마스터 위임")
    void delegatesMaster() throws Exception {
        // Given: 위임할 팀원과 인증 준비
        // When & Then
        mockMvc.perform(post("/api/v1/teams/{team-id}/members/{member-id}/delegate", 1L, 11L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent())
                .andDo(document("teams/delegate", pathParameters(teamId(), memberId())));
    }

    private static TeamResult teamResult() {
        return new TeamResult(
                1L,
                3L,
                "테스트 팀",
                OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    private static ParameterDescriptor teamId() {
        return parameterWithName("team-id").description("팀 식별자");
    }

    private static ParameterDescriptor memberId() {
        return parameterWithName("member-id").description("팀원 식별자");
    }

    private static FieldDescriptor[] teamFields() {
        return new FieldDescriptor[] {
            fieldWithPath("teamId").description("팀 식별자"),
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("name").description("팀 이름"),
            fieldWithPath("createdAt").description("생성 시각")
        };
    }

    private static FieldDescriptor[] teamListFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].teamId").description("팀 식별자"),
            fieldWithPath("[].cohortId").description("기수 식별자"),
            fieldWithPath("[].name").description("팀 이름"),
            fieldWithPath("[].createdAt").description("생성 시각")
        };
    }

    private static FieldDescriptor[] candidateFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].userId").description("사용자 식별자"),
            fieldWithPath("[].displayName").description("표시 이름"),
            fieldWithPath("[].email").description("이메일"),
            fieldWithPath("[].status").description("후보 상태")
        };
    }

    private static FieldDescriptor[] detailFields() {
        return new FieldDescriptor[] {
            fieldWithPath("teamId").description("팀 식별자"),
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("name").description("팀 이름"),
            fieldWithPath("createdAt").description("생성 시각"),
            fieldWithPath("memberCount").description("팀원 수"),
            fieldWithPath("myMemberId").description("내 팀원 식별자"),
            fieldWithPath("myRole").description("내 역할"),
            fieldWithPath("members").description("팀원 목록"),
            fieldWithPath("members[].memberId").description("팀원 식별자"),
            fieldWithPath("members[].displayName").description("표시 이름"),
            fieldWithPath("members[].role").description("역할"),
            fieldWithPath("members[].joinedAt").description("가입 시각")
        };
    }

    @Test
    @DisplayName("기수 생략 시 팀 생성")
    void createsTeamWithoutCohortId() throws Exception {
        // Given: 서비스가 사용자의 활성 기수를 선택한 응답
        when(teamService.create(null, "테스트 팀", USER_ID))
                .thenReturn(
                        new TeamResult(
                                1L,
                                3L,
                                "테스트 팀",
                                OffsetDateTime.parse("2026-07-24T10:00:00+09:00")));

        // When & Then
        mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"테스트 팀\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cohortId").value(3))
                .andDo(document(
                        "teams/create-without-cohort",
                        requestFields(fieldWithPath("name").description("팀 이름")),
                        responseFields(teamFields())));
        verify(teamService).create(null, "테스트 팀", USER_ID);
    }
}

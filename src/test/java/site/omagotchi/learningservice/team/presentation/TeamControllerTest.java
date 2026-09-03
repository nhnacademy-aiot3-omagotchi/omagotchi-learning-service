package site.omagotchi.learningservice.team.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberCandidateQueryService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateStatus;
import site.omagotchi.learningservice.team.application.result.TeamResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 팀 API의 요청자 식별.
 *
 * <p><b>요청자를 어디서 읽는지가 곧 권한이다.</b> 팀 API는 전부 "요청자가 이 팀의 MASTER인가"로
 * 갈리므로, 요청자를 헤더로 받으면 헤더 한 줄로 남의 팀을 해체할 수 있다. 그래서 이
 * 컨트롤러는 Access JWT의 {@code sub}만 읽는다.</p>
 *
 * <p>게이트웨이가 들어오는 {@code X-User-Id}를 제거하지만(default-filters) 그것에 기대지
 * 않는다 — 게이트웨이를 우회하는 내부 호출 경로가 ADR 0010으로 열려 있다.</p>
 */
class TeamControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPOOFED_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private TeamService teamService;
    private TeamMemberService teamMemberService;
    private TeamMasterService teamMasterService;
    private TeamMemberCandidateQueryService teamMemberCandidateQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        teamService = mock(TeamService.class);
        teamMemberService = mock(TeamMemberService.class);
        teamMasterService = mock(TeamMasterService.class);
        teamMemberCandidateQueryService = mock(TeamMemberCandidateQueryService.class);
        mockMvc = standaloneSetup(
                new TeamController(
                        teamService,
                        teamMemberService,
                        teamMasterService,
                        teamMemberCandidateQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                // @AuthenticationPrincipal은 Security의 Resolver가 있어야 풀린다.
                // standaloneSetup은 Spring Security 필터를 끼우지 않으므로 직접 등록한다.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("요청자를 JWT의 주체에서 읽는다.")
    void readsRequesterFromJwtSubject() throws Exception {
        when(teamService.getMyTeams(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/teams/me"))
                .andExpect(status().isOk());

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

        mockMvc.perform(get("/api/v1/teams/me").header("X-User-Id", SPOOFED_ID))
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
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/teams/1")
                        .header("X-User-Id", SPOOFED_ID))
                .andExpect(status().isNoContent());

        verify(teamMasterService).disband(1L, USER_ID);
    }

    @Test
    @DisplayName("팀 생성 응답은 201이고 요청자는 JWT의 주체다.")
    void createReturnsCreatedWithJwtSubject() throws Exception {
        when(teamService.create(3L, "테스트 팀", USER_ID)).thenReturn(new TeamResult(
                1L, 3L, "테스트 팀",
                OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9))));

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"cohortId\":3,\"name\":\"테스트 팀\"}"))
                .andExpect(status().isCreated());

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

        mockMvc.perform(get("/api/v1/teams/1/member-candidates")
                        .queryParam("query", "학생"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        verify(teamMemberCandidateQueryService).search(1L, "학생", USER_ID);
    }

    private static void authenticateAs(UUID userId) {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", "USER")
                .build();
        TestSecurityContextHolder.setAuthentication(new JwtAuthenticationToken(token));
    }
}

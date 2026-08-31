package site.omagotchi.learningservice.occupancy.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantQueryService;
import site.omagotchi.learningservice.occupancy.application.result.OccupancyParticipantResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateStatus;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 참여자 API의 HTTP 계약.
 *
 * <p>이탈과 제외가 같은 엔드포인트인 것을 여기서 고정한다. 경로를 나누면 클라이언트가
 * "내가 점유자인가"를 먼저 판단해 호출을 골라야 한다.</p>
 */
class OccupancyParticipantControllerTest {

    private static final String PATH = "/api/v1/spaces/1/occupancies/participants";
    private static final UUID REQUESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private OccupancyParticipantService occupancyParticipantService;
    private OccupancyParticipantQueryService occupancyParticipantQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        occupancyParticipantService = mock(OccupancyParticipantService.class);
        occupancyParticipantQueryService = mock(OccupancyParticipantQueryService.class);
        mockMvc = standaloneSetup(new OccupancyParticipantController(
                        occupancyParticipantService,
                        occupancyParticipantQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                // @AuthenticationPrincipal은 Security의 Resolver가 있어야 풀린다.
                // standaloneSetup은 Spring Security 필터를 끼우지 않으므로 직접 등록한다.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        authenticateAs(REQUESTER_ID);
    }

    /**
     * 보안 컨텍스트는 스레드에 붙으므로 반드시 비운다. 남기면 다음 테스트가 앞 테스트의
     * 요청자로 실행되어, 인증을 지워도 통과하는 테스트가 생긴다.
     */
    @AfterEach
    void tearDown() {
        TestSecurityContextHolder.clearContext();
    }

    /**
     * 요청자를 Access JWT로 세운다.
     *
     * <p>{@code standaloneSetup}은 Spring Security 필터를 끼우지 않아
     * {@code SecurityMockMvcRequestPostProcessors.jwt()}가 동작하지 않는다 — 그 후처리기는
     * 필터 체인이 읽어 가는 자리에 컨텍스트를 넣기 때문이다. 여기서는 컨텍스트를 직접 세우고
     * {@code AuthenticationPrincipalArgumentResolver}가 그것을 읽게 한다.</p>
     */
    private static void authenticateAs(UUID userId) {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", "USER")
                .build();
        TestSecurityContextHolder.setAuthentication(new JwtAuthenticationToken(token));
    }

    @Test
    @DisplayName("참여자를 추가하면 201을 응답한다.")
    void returns201OnAddParticipant() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + TARGET_ID + "\"}"))
                .andExpect(status().isCreated());

        verify(occupancyParticipantService).add(1L, TARGET_ID, REQUESTER_ID);
    }

    @Test
    @DisplayName("참여 후보 검색 결과를 응답한다.")
    void returnsParticipantCandidates() throws Exception {
        given(occupancyParticipantQueryService.searchCandidates(1L, "사용자", REQUESTER_ID))
                .willReturn(List.of(new ParticipantCandidateResult(
                        TARGET_ID,
                        "대상 사용자",
                        "target@example.com",
                        ParticipantCandidateStatus.AVAILABLE
                )));

        mockMvc.perform(get(PATH + "/candidates").queryParam("query", "사용자"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$[0].displayName").value("대상 사용자"))
                .andExpect(jsonPath("$[0].email").value("target@example.com"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("현재 참여자 상세 목록을 응답한다.")
    void returnsCurrentParticipants() throws Exception {
        given(occupancyParticipantQueryService.getParticipants(1L, REQUESTER_ID))
                .willReturn(List.of(new OccupancyParticipantResult(
                        REQUESTER_ID, "점유자", true)));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(REQUESTER_ID.toString()))
                .andExpect(jsonPath("$[0].displayName").value("점유자"))
                .andExpect(jsonPath("$[0].occupier").value(true));
    }

    @Test
    @DisplayName("대상 없이 추가를 요청하면 400을 응답한다.")
    void returns400WhenTargetMissing() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("타 기수 대상을 추가하면 400을 응답한다.")
    void returns400WhenTargetInDifferentCohort() throws Exception {
        assertAddError(OccupancyErrorCode.DIFFERENT_COHORT, 400);
    }

    @Test
    @DisplayName("점유자가 아니면 403을 응답한다.")
    void returns403WhenNotOccupier() throws Exception {
        assertAddError(OccupancyErrorCode.NOT_OCCUPIER, 403);
    }

    @Test
    @DisplayName("정원이 차면 409를 응답한다.")
    void returns409WhenCapacityExceeded() throws Exception {
        assertAddError(OccupancyErrorCode.CAPACITY_EXCEEDED, 409);
    }

    @Test
    @DisplayName("종료된 점유면 409를 응답한다.")
    void returns409WhenOccupancyEnded() throws Exception {
        assertAddError(OccupancyErrorCode.OCCUPANCY_ENDED, 409);
    }

    /** 자기 자신을 지정하면 이탈이다. 별도 엔드포인트가 없다. */
    @Test
    @DisplayName("본인을 지정해 이탈하면 204를 응답한다.")
    void returns204OnSelfLeave() throws Exception {
        mockMvc.perform(delete(PATH + "/" + REQUESTER_ID))
                .andExpect(status().isNoContent());

        verify(occupancyParticipantService).remove(1L, REQUESTER_ID, REQUESTER_ID);
    }

    @Test
    @DisplayName("다른 사람을 지정해 제외하면 204를 응답한다.")
    void returns204OnKickingOther() throws Exception {
        mockMvc.perform(delete(PATH + "/" + TARGET_ID))
                .andExpect(status().isNoContent());

        verify(occupancyParticipantService).remove(1L, TARGET_ID, REQUESTER_ID);
    }

    @Test
    @DisplayName("점유자를 이탈시키려 하면 400과 반납 안내를 응답한다.")
    void returns400WithReleaseGuidanceWhenTargetingOccupier() throws Exception {
        doThrow(new BusinessException(OccupancyErrorCode.OCCUPIER_CANNOT_LEAVE))
                .when(occupancyParticipantService).remove(any(), any(), any());

        mockMvc.perform(delete(PATH + "/" + REQUESTER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_OCCUPIER_CANNOT_LEAVE"));
    }

    @Test
    @DisplayName("참여자가 아니면 404를 응답한다.")
    void returns404WhenNotAParticipant() throws Exception {
        doThrow(new BusinessException(OccupancyErrorCode.PARTICIPANT_NOT_FOUND))
                .when(occupancyParticipantService).remove(any(), any(), any());

        mockMvc.perform(delete(PATH + "/" + TARGET_ID))
                .andExpect(status().isNotFound());
    }

    private void assertAddError(ErrorCode errorCode, int expectedStatus) throws Exception {
        doThrow(new BusinessException(errorCode))
                .when(occupancyParticipantService).add(any(), any(), any());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + TARGET_ID + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.code()))
                .andExpect(jsonPath("$.path").value(PATH));
    }

}

package site.omagotchi.learningservice.occupancy.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantQueryService;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.result.OccupancyParticipantResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateStatus;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

/**
 * 참여자 API의 HTTP 계약.
 *
 * <p>이탈과 제외가 같은 엔드포인트인 것을 여기서 고정한다. 경로를 나누면 클라이언트가 "내가 점유자인가"를 먼저 판단해 호출을 골라야 한다.
 */
@WebMvcTest(controllers = OccupancyParticipantController.class)
@LearningRestDocsTest
class OccupancyParticipantControllerTest {

    private static final String PATH = "/api/v1/spaces/1/occupancies/participants";
    private static final UUID REQUESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String AUTHORIZATION =
            "Bearer "
                    + TestJwtKeyConfig.issue(
                            TestJwtKeyConfig.ISSUER,
                            TestJwtKeyConfig.AUDIENCE,
                            REQUESTER_ID.toString(),
                            "USER");

    @MockitoBean
    private OccupancyParticipantService occupancyParticipantService;

    @MockitoBean
    private OccupancyParticipantQueryService occupancyParticipantQueryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("참여자를 추가하면 201을 응답한다.")
    void returns201OnAddParticipant() throws Exception {
        mockMvc.perform(post("/api/v1/spaces/{space-id}/occupancies/participants", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + TARGET_ID + "\"}"))
                .andDo(document(
                        "occupancy/participant-add",
                        pathParameters(parameterWithName("space-id").description("공간 ID")),
                        requestFields(
                                fieldWithPath("targetUserId")
                                        .type(JsonFieldType.STRING)
                                        .description("추가할 사용자 ID"))))
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

        mockMvc.perform(get("/api/v1/spaces/{space-id}/occupancies/participants/candidates", 1L)
                        .queryParam("query", "사용자")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andDo(document(
                        "occupancy/participant-candidates",
                        pathParameters(parameterWithName("space-id").description("공간 ID")),
                        queryParameters(parameterWithName("query").description("검색어")),
                        responseFields(candidateFields())))
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
                .willReturn(List.of(new OccupancyParticipantResult(REQUESTER_ID, "점유자", true)));

        mockMvc.perform(get("/api/v1/spaces/{space-id}/occupancies/participants", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andDo(document(
                        "occupancy/participant-list",
                        pathParameters(parameterWithName("space-id").description("공간 ID")),
                        responseFields(participantFields())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(REQUESTER_ID.toString()))
                .andExpect(jsonPath("$[0].displayName").value("점유자"))
                .andExpect(jsonPath("$[0].occupier").value(true));
    }

    @Test
    @DisplayName("대상 없이 추가를 요청하면 400을 응답한다.")
    void returns400WhenTargetMissing() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
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
        mockMvc.perform(delete(PATH + "/" + REQUESTER_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNoContent());

        verify(occupancyParticipantService).remove(1L, REQUESTER_ID, REQUESTER_ID);
    }

    @Test
    @DisplayName("다른 사람을 지정해 제외하면 204를 응답한다.")
    void returns204OnKickingOther() throws Exception {
        mockMvc.perform(delete(
                                "/api/v1/spaces/{space-id}/occupancies/participants/{target-user-id}",
                                1L,
                                TARGET_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andDo(document(
                        "occupancy/participant-remove",
                        pathParameters(
                                parameterWithName("space-id").description("공간 ID"),
                                parameterWithName("target-user-id")
                                        .description("대상 사용자 ID"))))
                .andExpect(status().isNoContent());

        verify(occupancyParticipantService).remove(1L, TARGET_ID, REQUESTER_ID);
    }

    @Test
    @DisplayName("점유자를 이탈시키려 하면 400과 반납 안내를 응답한다.")
    void returns400WithReleaseGuidanceWhenTargetingOccupier() throws Exception {
        doThrow(new BusinessException(OccupancyErrorCode.OCCUPIER_CANNOT_LEAVE))
                .when(occupancyParticipantService).remove(any(), any(), any());

        mockMvc.perform(delete(PATH + "/" + REQUESTER_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_OCCUPIER_CANNOT_LEAVE"));
    }

    @Test
    @DisplayName("참여자가 아니면 404를 응답한다.")
    void returns404WhenNotAParticipant() throws Exception {
        doThrow(new BusinessException(OccupancyErrorCode.PARTICIPANT_NOT_FOUND))
                .when(occupancyParticipantService).remove(any(), any(), any());

        mockMvc.perform(delete(PATH + "/" + TARGET_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNotFound());
    }

    private FieldDescriptor[] participantFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].userId").type(JsonFieldType.STRING).description("사용자 ID"),
            fieldWithPath("[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("[].occupier").type(JsonFieldType.BOOLEAN).description("점유자 여부")
        };
    }

    private FieldDescriptor[] candidateFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].userId").type(JsonFieldType.STRING).description("사용자 ID"),
            fieldWithPath("[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("[].email").type(JsonFieldType.STRING).description("이메일"),
            fieldWithPath("[].status").type(JsonFieldType.STRING).description("참여 가능 상태")
        };
    }

    private void assertAddError(ErrorCode errorCode, int expectedStatus) throws Exception {
        doThrow(new BusinessException(errorCode))
                .when(occupancyParticipantService).add(any(), any(), any());

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + TARGET_ID + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.code()))
                .andExpect(jsonPath("$.path").value(PATH));
    }

}

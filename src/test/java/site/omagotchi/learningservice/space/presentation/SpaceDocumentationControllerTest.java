package site.omagotchi.learningservice.space.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.space.application.LabAccessQueryService;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SelectableLabView;
import site.omagotchi.learningservice.space.application.result.SpacePresenceDetailResult;
import site.omagotchi.learningservice.space.application.result.SpacePresenceOccupantResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = {SelectableLabController.class, SpaceAdminPresenceController.class})
@LearningRestDocsTest
class SpaceDocumentationControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final String AUTHORIZATION = "Bearer " + TestJwtKeyConfig.issue();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LabAccessQueryService labAccessQueryService;

    @MockitoBean
    private SpaceQueryService spaceQueryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Test
    @DisplayName("선택 가능한 실습실 목록 조회")
    void listsSelectableLabs() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(labAccessQueryService.findSelectableLabs(10L, USER_ID))
                .willReturn(List.of(new SelectableLabView(1L, "실습실 A", 20, 3L)));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/spaces/labs", 10L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/list-selectable-labs",
                        pathParameters(parameterWithName("cohort-id").description("기수 ID")),
                        responseFields(
                                fieldWithPath("[].spaceId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("실습실 ID"),
                                fieldWithPath("[].name")
                                        .type(JsonFieldType.STRING)
                                        .description("실습실 이름"),
                                fieldWithPath("[].capacity")
                                        .type(JsonFieldType.NUMBER)
                                        .description("수용 인원"),
                                fieldWithPath("[].reservedCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("예약 인원"))));
    }

    @Test
    @DisplayName("공간 현재 인원 조회")
    void getsSpacePresences() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(spaceQueryService.getCurrentPresences(10L, 1L, USER_ID))
                .willReturn(
                        new SpacePresenceDetailResult(
                                1L,
                                3L,
                                2L,
                                1L,
                                List.of(new SpacePresenceOccupantResult(USER_ID, "오마"))));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/admin/cohorts/{cohort-id}/spaces/{space-id}/presences",
                                10L,
                                1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/get-presences",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 ID"),
                                parameterWithName("space-id").description("공간 ID")),
                        responseFields(
                                fieldWithPath("spaceId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("totalCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("전체 인원"),
                                fieldWithPath("cohortCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("현재 기수 인원"),
                                fieldWithPath("otherCohortCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("다른 기수 인원"),
                                fieldWithPath("occupants")
                                        .type(JsonFieldType.ARRAY)
                                        .description("현재 기수 이용자 목록"),
                                fieldWithPath("occupants[].userId")
                                        .type(JsonFieldType.STRING)
                                        .description("사용자 ID"),
                                fieldWithPath("occupants[].displayName")
                                        .type(JsonFieldType.STRING)
                                        .description("표시 이름"))));
    }
}

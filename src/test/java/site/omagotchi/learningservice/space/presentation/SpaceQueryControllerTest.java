package site.omagotchi.learningservice.space.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(SpaceQueryController.class)
@LearningRestDocsTest
class SpaceQueryControllerTest {

    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private SpaceQueryService spaceQueryService;

    @Test
    void returnsEmptyJsonArrayWhenNoSpacesExist() throws Exception {
        when(spaceQueryService.getSpaceList(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(spaceQueryService).getSpaceList(null);
    }

    @Test
    void returnsActualResponseFieldsAndSerializedEnums() throws Exception {
        ZonedDateTime expiresAt = ZonedDateTime.of(
                2026, 7, 27, 15, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );
        when(spaceQueryService.getSpaceList(null)).thenReturn(List.of(
                new SpaceListResult(
                        1L,
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        11L,
                        SpaceUsageStatus.OCCUPIED,
                        expiresAt,
                        1800L,
                        false,
                        null,
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceId").value(1))
                .andExpect(jsonPath("$[0].name").value("회의실 A"))
                .andExpect(jsonPath("$[0].type").value("MEETING"))
                .andExpect(jsonPath("$[0].capacity").value(8))
                .andExpect(jsonPath("$[0].operationalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].inactiveReason").isEmpty())
                .andExpect(jsonPath("$[0].cohortId").value(11))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$[0].occupancyExpiresAt").value("2026-07-27T15:00:00+09:00"))
                .andExpect(jsonPath("$[0].remainingTimeSeconds").value(1800))
                .andExpect(jsonPath("$[0].occupiedBySameCohort").value(false))
                .andDo(document(
                        "spaces/list",
                        responseFields(
                                fieldWithPath("[].spaceId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("[].name")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 이름"),
                                fieldWithPath("[].type")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 유형"),
                                fieldWithPath("[].capacity")
                                        .type(JsonFieldType.NUMBER)
                                        .description("수용 인원"),
                                fieldWithPath("[].operationalStatus")
                                        .type(JsonFieldType.STRING)
                                        .description("운영 상태"),
                                fieldWithPath("[].inactiveReason")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("비활성화 사유"),
                                fieldWithPath("[].cohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("배정된 기수 ID"),
                                fieldWithPath("[].status")
                                        .type(JsonFieldType.STRING)
                                        .description("현재 사용 상태"),
                                fieldWithPath("[].occupancyExpiresAt")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("점유 만료 시각"),
                                fieldWithPath("[].remainingTimeSeconds")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("남은 점유 시간 (초)"),
                                fieldWithPath("[].occupiedBySameCohort")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("같은 기수의 점유 여부"),
                                fieldWithPath("[].occupancyCohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("점유 중인 기수 ID"),
                                fieldWithPath("[].occupierMembershipId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("점유자 멤버십 ID"),
                                fieldWithPath("[].occupierUserId")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("점유자 사용자 ID"),
                                fieldWithPath("[].participantUserIds")
                                        .type(JsonFieldType.ARRAY)
                                        .optional()
                                        .description("참여 사용자 ID 목록"),
                                fieldWithPath("[].currentPresenceCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("현재 Presence 인원"))));

        verify(spaceQueryService).getSpaceList(null);
    }

    @Test
    void usesAuthenticatedPrincipalInsteadOfUserHeader() throws Exception {
        UUID spoofedUserId = UUID.randomUUID();
        when(spaceQueryService.getSpaceList(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/spaces")
                        .with(jwt().jwt(token -> token
                                .subject(USER_ID.toString())
                                .claim("role", "USER")))
                        .header("X-User-Id", spoofedUserId))
                .andExpect(status().isOk());

        verify(spaceQueryService).getSpaceList(USER_ID);
    }
}

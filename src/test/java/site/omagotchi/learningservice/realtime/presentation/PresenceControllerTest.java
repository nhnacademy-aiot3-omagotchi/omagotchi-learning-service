package site.omagotchi.learningservice.realtime.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.realtime.application.CohortPresenceService;
import site.omagotchi.learningservice.realtime.application.CohortPresenceSnapshot;
import site.omagotchi.learningservice.realtime.application.PresenceCharacterSnapshot;
import site.omagotchi.learningservice.realtime.application.PresenceStatus;
import site.omagotchi.learningservice.realtime.application.PresenceUserSnapshot;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = PresenceController.class)
@DisplayName("기수 Presence API 계약")
@LearningRestDocsTest
class PresenceControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final String PRESENCE_SESSION_ID = "presence-session-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private CohortPresenceService presenceService;

    @Test
    @DisplayName("현재 사용자의 기수 Presence snapshot을 반환한다")
    void getsMyCohortPresence() throws Exception {
        given(presenceService.currentUserSnapshot(USER_ID)).willReturn(snapshot());

        mockMvc.perform(get("/api/v1/cohorts/me/presence")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.users[0].status").value("ONLINE"))
                .andExpect(jsonPath("$.users[0].nickname").value("오마"))
                .andExpect(jsonPath("$.users[0].currentCharacter.type").value("night"))
                .andExpect(jsonPath("$.users[0].currentCharacter.colorId").value("pistachio"))
                .andExpect(
                        jsonPath("$.users[0].currentCharacter.assetKey").value("night/pistachio"))
                .andExpect(jsonPath("$.occurredAt").value("2026-08-20T15:00:00+09:00"))
                .andDo(document(
                        "presence/get-my-cohort-snapshot",
                        responseFields(snapshotFields())));

        verify(presenceService).currentUserSnapshot(USER_ID);
    }

    @Test
    @DisplayName("heartbeat는 Presence 세션을 갱신하고 최신 snapshot을 반환한다")
    void refreshesPresenceHeartbeat() throws Exception {
        given(presenceService.currentUserSnapshot(USER_ID)).willReturn(snapshot());

        mockMvc.perform(post("/api/v1/cohorts/me/presence/heartbeat")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .header(
                                PresenceController.PRESENCE_SESSION_HEADER,
                                PRESENCE_SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.users[0].status").value("ONLINE"))
                .andDo(document(
                        "presence/heartbeat",
                        requestHeaders(
                                headerWithName(PresenceController.PRESENCE_SESSION_HEADER)
                                        .description("Frontend BFF에서 발급한 재실 세션 식별자")),
                        responseFields(snapshotFields())));

        // 조회를 위해 한 번 더 왕복하지 않도록 heartbeat 응답에 snapshot을 실어 보낸다.
        verify(presenceService).heartbeat(
                PRESENCE_SESSION_ID,
                new AuthenticatedUser(USER_ID, GlobalRole.USER)
        );
        verify(presenceService).currentUserSnapshot(USER_ID);
    }

    @Test
    @DisplayName("X-Presence-Session 헤더가 없으면 400으로 거부한다")
    void rejectsHeartbeatWithoutPresenceSessionHeader() throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/me/presence/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(presenceService, never()).heartbeat(any(), any());
    }

    @Test
    @DisplayName("X-Presence-Session 헤더가 공백이면 400으로 거부한다")
    void rejectsHeartbeatWithBlankPresenceSessionHeader() throws Exception {
        // CohortPresenceService는 빈 sessionId를 조용히 무시하므로,
        // 막지 않으면 아무것도 등록하지 않은 채 200이 나가 장애가 숨는다.
        mockMvc.perform(post("/api/v1/cohorts/me/presence/heartbeat")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .header(PresenceController.PRESENCE_SESSION_HEADER, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRESENCE_SESSION_ID_REQUIRED"))
                .andDo(document(
                        "presence/heartbeat-invalid-session",
                        requestHeaders(
                                headerWithName(PresenceController.PRESENCE_SESSION_HEADER)
                                        .description("공백으로 전달된 Presence 세션 식별자")),
                        responseFields(errorFields())));

        verify(presenceService, never()).heartbeat(any(), any());
    }

    @Test
    @DisplayName("이탈 통지는 204를 반환하고 Presence 세션을 종료한다")
    void leavesPresence() throws Exception {
        mockMvc.perform(delete("/api/v1/cohorts/me/presence")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .header(
                                PresenceController.PRESENCE_SESSION_HEADER,
                                PRESENCE_SESSION_ID))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "presence/leave",
                        requestHeaders(
                                headerWithName(PresenceController.PRESENCE_SESSION_HEADER)
                                        .description("종료할 Presence 세션 식별자"))));

        verify(presenceService).disconnectSession(PRESENCE_SESSION_ID, USER_ID);
    }

    @Test
    @DisplayName("인증 없이 heartbeat를 보내면 401로 거부한다")
    void rejectsUnauthenticatedHeartbeat() throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/me/presence/heartbeat")
                        .header(PresenceController.PRESENCE_SESSION_HEADER, PRESENCE_SESSION_ID))
                .andExpect(status().isUnauthorized());

        verify(presenceService, never()).heartbeat(any(), any());
    }

    private static CohortPresenceSnapshot snapshot() {
        return new CohortPresenceSnapshot(
                1L,
                List.of(new PresenceUserSnapshot(
                        USER_ID,
                        "오마",
                        new PresenceCharacterSnapshot("night", "pistachio", "night/pistachio"),
                        PresenceStatus.ONLINE
                )),
                OffsetDateTime.parse("2026-08-20T15:00:00+09:00")
        );
    }

    private static FieldDescriptor[] snapshotFields() {
        return new FieldDescriptor[] {
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("users").description("현재 Presence 사용자 목록"),
            fieldWithPath("users[].userId").description("사용자 식별자"),
            fieldWithPath("users[].nickname").description("사용자 닉네임"),
            fieldWithPath("users[].currentCharacter").description("대표 캐릭터 정보"),
            fieldWithPath("users[].currentCharacter.type").description("캐릭터 타입"),
            fieldWithPath("users[].currentCharacter.colorId").description("캐릭터 색상 식별자"),
            fieldWithPath("users[].currentCharacter.assetKey").description("캐릭터 에셋 키"),
            fieldWithPath("users[].status").description("재실 상태"),
            fieldWithPath("occurredAt").description("스냅샷 생성 시각")
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

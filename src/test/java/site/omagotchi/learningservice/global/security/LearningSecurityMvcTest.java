package site.omagotchi.learningservice.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.presentation.SpaceAdminController;
import site.omagotchi.learningservice.space.presentation.SpaceQueryController;
import site.omagotchi.learningservice.rule.application.ThresholdRuleService;
import site.omagotchi.learningservice.rule.presentation.ThresholdRuleController;
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.presentation.TelegramController;
import site.omagotchi.learningservice.telegram.presentation.TelegramWebhookController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ThresholdRuleController.class,
        TelegramController.class,
        TelegramWebhookController.class,
        SpaceAdminController.class,
        SpaceQueryController.class
})
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
class LearningSecurityMvcTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID SPOOFED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramUserLinkService telegramUserLinkService;

    @MockitoBean
    private SpaceCommandService spaceCommandService;

    @MockitoBean
    private SpaceQueryService spaceQueryService;

    @MockitoBean
    private ThresholdRuleService thresholdRuleService;

    @Test
    @DisplayName("Telegram webhook은 Access JWT 없이 호출")
    void permitsTelegramWebhookWithoutToken() throws Exception {
        // Given
        given(telegramUserLinkService.linkByWebhook(any()))
                .willReturn(linkResponse(USER_ID));

        // When
        ResultActions result = mockMvc.perform(post("/api/v1/webhooks/telegram")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // Then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호 API는 Access JWT가 없으면 401")
    void rejectsProtectedRequestWithoutToken() throws Exception {
        // When
        ResultActions result = mockMvc.perform(get("/api/v1/telegram/link"));

        // Then
        result
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Bearer")
                ))
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/v1/telegram/link"));
        verifyNoInteractions(telegramUserLinkService);
    }

    @Test
    @DisplayName("공간 목록 API는 Access JWT 없이 호출")
    void permitsAnonymousSpaceList() throws Exception {
        given(spaceQueryService.getSpaceList(null)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(spaceQueryService).getSpaceList(null);
    }

    @Test
    @DisplayName("외부 사용자 Header 대신 JWT sub를 행위자로 사용")
    void usesJwtSubjectInsteadOfSpoofedHeader() throws Exception {
        // Given
        given(telegramUserLinkService.getMyLink(USER_ID))
                .willReturn(linkResponse(USER_ID));

        // When
        ResultActions result = mockMvc.perform(get("/api/v1/telegram/link")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                .header("X-User-Id", SPOOFED_USER_ID)
                .header("X-Global-Role", "SYSTEM_ADMIN"));

        // Then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
        verify(telegramUserLinkService).getMyLink(USER_ID);
    }

    @Test
    @DisplayName("USER의 시스템 관리자 API 접근은 403")
    void rejectsUserFromSystemAdminEndpoint() throws Exception {
        // Given
        String userToken = TestJwtKeyConfig.issue("USER");

        // When
        ResultActions result = mockMvc.perform(post("/api/v1/cohorts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // Then
        result
                .andExpect(status().isForbidden())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"insufficient_scope\"")
                ))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("공간 관리자 API가 위조 Header 대신 JWT 행위자를 사용")
    void spaceAdminUsesJwtActorInsteadOfSpoofedHeaders() throws Exception {
        String userToken = TestJwtKeyConfig.issue("USER");

        mockMvc.perform(delete("/api/v1/admin/spaces/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .header("X-User-Id", SPOOFED_USER_ID)
                        .header("X-Global-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isNoContent());

        verify(spaceCommandService).delete(
                1L,
                USER_ID);
    }

    @Test
    @DisplayName("USER의 임계치 룰 생성은 403")
    void rejectsUserFromCreatingThresholdRule() throws Exception {
        // 임계치 룰은 rule-service 판정 동작을 바꾸는 운영 설정이라 일반 사용자에게 열려 있으면 안 된다
        String userToken = TestJwtKeyConfig.issue("USER");

        mockMvc.perform(post("/api/v1/threshold-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verifyNoInteractions(thresholdRuleService);
    }

    @Test
    @DisplayName("USER의 임계치 룰 수정은 403")
    void rejectsUserFromUpdatingThresholdRule() throws Exception {
        String userToken = TestJwtKeyConfig.issue("USER");

        mockMvc.perform(patch("/api/v1/threshold-rules/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verifyNoInteractions(thresholdRuleService);
    }

    @Test
    @DisplayName("임계치 룰 조회는 USER도 허용 - rule-service RuleSyncClient 의 적재 경로다")
    void allowsUserToReadThresholdRules() throws Exception {
        given(thresholdRuleService.readAll()).willReturn(List.of());
        String userToken = TestJwtKeyConfig.issue("USER");

        mockMvc.perform(get("/api/v1/threshold-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());

        verify(thresholdRuleService).readAll();
    }

    private TelegramUserLinkResponse linkResponse(UUID userId) {
        return new TelegramUserLinkResponse(
                userId,
                1L,
                2L,
                true,
                OffsetDateTime.parse("2026-07-29T00:00:00Z"),
                null
        );
    }
}

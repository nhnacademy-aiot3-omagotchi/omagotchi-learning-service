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
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.presentation.TelegramController;
import site.omagotchi.learningservice.telegram.presentation.TelegramWebhookController;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        TelegramController.class,
        TelegramWebhookController.class
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

    @Test
    @DisplayName("Telegram webhook은 Access JWT 없이 호출")
    void permitsTelegramWebhookWithoutToken() throws Exception {
        // Given
        given(telegramUserLinkService.linkByWebhook(any()))
                .willReturn(linkResponse(USER_ID));

        // When
        ResultActions result = mockMvc.perform(post("/api/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // Then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호 API는 Access JWT가 없으면 401")
    void rejectsProtectedRequestWithoutToken() throws Exception {
        // When
        ResultActions result = mockMvc.perform(get("/api/telegram/link"));

        // Then
        result
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Bearer")
                ))
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/telegram/link"));
        verifyNoInteractions(telegramUserLinkService);
    }

    @Test
    @DisplayName("외부 사용자 Header 대신 JWT sub를 행위자로 사용")
    void usesJwtSubjectInsteadOfSpoofedHeader() throws Exception {
        // Given
        given(telegramUserLinkService.getMyLink(USER_ID))
                .willReturn(linkResponse(USER_ID));

        // When
        ResultActions result = mockMvc.perform(get("/api/telegram/link")
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
        ResultActions result = mockMvc.perform(post("/api/cohorts")
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

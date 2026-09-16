package site.omagotchi.learningservice.telegram.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.application.TelegramWebhookService;
import site.omagotchi.learningservice.telegram.application.command.UpdateTelegramNotificationCommand;
import site.omagotchi.learningservice.telegram.application.result.TelegramLinkTokenResult;
import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResult;

@WebMvcTest(controllers = {TelegramController.class, TelegramWebhookController.class})
@LearningRestDocsTest
class TelegramDocumentationTest {
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final OffsetDateTime EXPIRES = OffsetDateTime.parse("2026-09-21T09:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private TelegramUserLinkService userLinkService;

    @MockitoBean
    private TelegramWebhookService webhookService;

    @MockitoBean
    private TelegramWebhookAuthenticator authenticator;

    @Test
    @DisplayName("Telegram 연동 토큰 발급")
    void issuesLinkToken() throws Exception {
        // Given: Telegram 연결 토큰 발급에 필요한 요청과 서비스 응답을 준비한다.
        given(userLinkService.issueLinkToken(USER_ID))
                .willReturn(new TelegramLinkTokenResult("https://t.me/example?start=TOKEN_PLACEHOLDER", EXPIRES));

        // When & Then
        mockMvc.perform(post("/api/v1/telegram/link-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andDo(document(
                        "telegram/issue-link-token",
                        responseFields(
                                fieldWithPath("linkUrl").description("Telegram 봇 딥링크"),
                                fieldWithPath("expiresAt").description("토큰 만료 시각"))));
    }

    @Test
    @DisplayName("Telegram 연동 조회")
    void getsMyLink() throws Exception {
        // Given: Telegram 연결 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(userLinkService.getMyLink(USER_ID)).willReturn(link());

        // When & Then
        mockMvc.perform(get("/api/v1/telegram/link").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andDo(document("telegram/get-link", responseFields(linkFields())));
    }

    @Test
    @DisplayName("Telegram 알림 설정 변경")
    void updatesNotification() throws Exception {
        // Given: Telegram 알림 설정 변경에 필요한 요청과 서비스 응답을 준비한다.
        given(
                        userLinkService.updateNotification(
                                eq(USER_ID), eq(new UpdateTelegramNotificationCommand(false))))
                .willReturn(link());

        // When & Then
        mockMvc.perform(patch("/api/v1/telegram/link/notification")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "telegram/update-notification",
                        requestFields(fieldWithPath("enabled").description("알림 수신 여부")),
                        responseFields(linkFields())));
    }

    @Test
    @DisplayName("Telegram 연동 해제")
    void disconnects() throws Exception {
        // Given: Telegram 연결 해제에 필요한 요청과 서비스 응답을 준비한다.
        given(userLinkService.disconnect(USER_ID)).willReturn(link());

        // When & Then
        mockMvc.perform(delete("/api/v1/telegram/link").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andDo(document("telegram/disconnect", responseFields(linkFields())));
    }

    @Test
    @DisplayName("Telegram 웹훅 수신")
    void receivesWebhook() throws Exception {
        // Given: Telegram webhook 수신에 필요한 요청과 서비스 응답을 준비한다.
        given(authenticator.isTelegram("WEBHOOK_SECRET_PLACEHOLDER")).willReturn(true);

        // When & Then
        mockMvc.perform(post("/api/v1/webhooks/telegram")
                        .header(
                                "X-Telegram-Bot-Api-Secret-Token",
                                "WEBHOOK_SECRET_PLACEHOLDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"update_id\":1001,\"message\":{\"chat\":{\"id\":3001,\"type\":\"private\"},\"from\":{\"id\":2001,\"is_bot\":false,\"first_name\":\"사용자\",\"username\":\"user\"},\"text\":\"/start TOKEN_PLACEHOLDER\"}}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "telegram/webhook",
                        requestHeaders(
                                headerWithName("X-Telegram-Bot-Api-Secret-Token")
                                        .description("Telegram에서 설정한 웹훅 비밀 토큰")),
                        requestFields(
                                fieldWithPath("update_id")
                                        .description("Telegram 업데이트 식별자"),
                                fieldWithPath("message").description("메시지"),
                                fieldWithPath("message.chat").description("채팅 정보"),
                                fieldWithPath("message.chat.id").description("채팅 식별자"),
                                fieldWithPath("message.chat.type")
                                        .description("채팅 유형. private만 연동 처리"),
                                fieldWithPath("message.from").description("발신자 정보"),
                                fieldWithPath("message.from.id")
                                        .description("Telegram 사용자 식별자"),
                                fieldWithPath("message.from.is_bot").description("봇 여부"),
                                fieldWithPath("message.from.first_name")
                                        .description("이름")
                                        .optional(),
                                fieldWithPath("message.from.username")
                                        .description("사용자명")
                                        .optional(),
                                fieldWithPath("message.text")
                                        .description("메시지 텍스트")
                                        .optional())));
    }

    private TelegramUserLinkResult link() {
        return new TelegramUserLinkResult(
                USER_ID,
                2001L,
                3001L,
                true,
                OffsetDateTime.parse("2026-09-14T09:00:00+09:00"),
                null);
    }

    private String bearer() {
        return "Bearer " + TestJwtKeyConfig.issue();
    }

    private static FieldDescriptor[] linkFields() {
        return new FieldDescriptor[] {
            fieldWithPath("userId").description("사용자 식별자"),
            fieldWithPath("telegramUserId").description("Telegram 사용자 식별자"),
            fieldWithPath("telegramChatId").description("Telegram 채팅 식별자"),
            fieldWithPath("notificationEnabled").description("알림 수신 여부"),
            fieldWithPath("linkedAt").description("연동 시각"),
            fieldWithPath("disconnectedAt").description("연동 해제 시각 (연동 중이면 null)")
        };
    }
}

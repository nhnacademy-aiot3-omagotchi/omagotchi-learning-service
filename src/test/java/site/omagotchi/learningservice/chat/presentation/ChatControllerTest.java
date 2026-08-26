package site.omagotchi.learningservice.chat.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import site.omagotchi.learningservice.global.security.*;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@DisplayName("AI 채팅 API")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClient geminiChatClient;

    @Test
    @DisplayName("인증 없이 호출하면 401을 반환한다")
    void requiresAuthentication() throws Exception {
        this.mockMvc.perform(get("/api/v1/chat").param("question", "서울 날씨 알려줘"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(this.geminiChatClient);
    }

    @Test
    @DisplayName("question 파라미터가 없으면 400을 반환한다")
    void rejectsMissingQuestion() throws Exception {
        this.mockMvc.perform(get("/api/v1/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(this.geminiChatClient);
    }

    @Test
    @DisplayName("question이 공백뿐이면 400을 반환한다")
    void rejectsBlankQuestion() throws Exception {
        this.mockMvc.perform(get("/api/v1/chat")
                        .param("question", "   ")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(this.geminiChatClient);
    }

    @Test
    @DisplayName("question이 최대 길이를 넘으면 400을 반환한다")
    void rejectsTooLongQuestion() throws Exception {
        String tooLong = "가".repeat(1001);

        this.mockMvc.perform(get("/api/v1/chat")
                        .param("question", tooLong)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(this.geminiChatClient);
    }

    @Test
    @DisplayName("질문을 그대로 ChatClient에 전달한다")
    void passesQuestionToChatClient() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = stubChatClientChain();

        this.mockMvc.perform(get("/api/v1/chat")
                        .param("question", "광주 동구 날씨 알려줘")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andReturn();

        verify(requestSpec).user("광주 동구 날씨 알려줘");
    }

    @Test
    @DisplayName("대화 ID를 JWT의 사용자 ID로 설정한다")
    void usesAuthenticatedUserIdAsConversationId() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = stubChatClientChain();

        this.mockMvc.perform(get("/api/v1/chat")
                        .param("question", "서울 날씨 알려줘")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andReturn();

        // advisors(...)에 넘긴 설정을 꺼내 직접 실행해서 어떤 대화 ID를 넣었는지 확인한다.
        // 다른 사용자의 대화가 섞이지 않으려면 반드시 JWT에서 온 사용자 ID여야 한다.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(requestSpec).advisors(captor.capture());

        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        captor.getValue().accept(advisorSpec);

        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, TestJwtKeyConfig.USER_ID);
    }

    private ChatClient.ChatClientRequestSpec stubChatClientChain() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        given(this.geminiChatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.advisors(any(Consumer.class))).willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamResponseSpec);
        given(streamResponseSpec.content()).willReturn(Flux.just("흐리고 33도입니다."));

        return requestSpec;
    }

    private static String bearerToken() {
        return "Bearer " + TestJwtKeyConfig.issue();
    }
}
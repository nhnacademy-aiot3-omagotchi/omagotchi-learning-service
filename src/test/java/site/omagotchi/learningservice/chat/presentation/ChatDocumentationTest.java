package site.omagotchi.learningservice.chat.presentation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseBody;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = ChatController.class)
@LearningRestDocsTest
class ChatDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean(name = "geminiChatClient")
    private ChatClient geminiChatClient;

    @MockitoBean(name = "ollamaChatClient")
    private ChatClient ollamaChatClient;

    @Test
    @DisplayName("SSE 채팅 응답 스트리밍")
    void streamsChatAnswer() throws Exception {
        // Given: Gemini 스트림이 두 개의 SSE 문장을 반환한다.
        ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = Mockito.mock(ChatClient.StreamResponseSpec.class);
        given(geminiChatClient.prompt()).willReturn(spec);
        given(spec.user(anyString())).willReturn(spec);
        given(spec.toolContext(anyMap())).willReturn(spec);
        given(spec.advisors(any(Consumer.class))).willReturn(spec);
        given(spec.stream()).willReturn(stream);
        given(stream.content()).willReturn(Flux.just("첫 문장", "둘째 문장"));

        // When: 채팅 스트림을 시작하고 비동기 처리를 완료한다.
        MvcResult initial = mockMvc.perform(get("/api/v1/chat")
                        .param("question", "서울 날씨 알려줘")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        // Then: UTF-8 SSE 응답과 두 문장 및 REST Docs를 검증한다.
        mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(
                        response ->
                                assertTrue(
                                        response.getResponse()
                                                .getContentAsString(StandardCharsets.UTF_8)
                                                .contains("data:첫 문장")))
                .andExpect(
                        response ->
                                assertTrue(
                                        response.getResponse()
                                                .getContentAsString(StandardCharsets.UTF_8)
                                                .contains("data:둘째 문장")))
                .andDo(document(
                        "chat/stream",
                        queryParameters(
                                parameterWithName("question")
                                        .description("질문 (공백 제외 1~1000자)"),
                                parameterWithName("model")
                                        .description("사용할 모델 (`GEMINI` 또는 `OLLAMA`, 기본값 `GEMINI`)")
                                        .optional()),
                        responseHeaders(
                                headerWithName(HttpHeaders.CONTENT_TYPE)
                                        .description("SSE 응답 (`text/event-stream`)")),
                        responseBody()));
    }

    @Test
    @DisplayName("인증 없는 채팅 요청 거절")
    void rejectsMissingJwt() throws Exception {
        // Given: 인증 없는 채팅 요청 거절

        // When & Then
        mockMvc.perform(get("/api/v1/chat").param("question", "질문"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"))
                .andDo(document("chat/missing-jwt", responseFields(errorFields())));
    }

    @Test
    @DisplayName("빈 질문 거절")
    void rejectsBlankQuestion() throws Exception {
        // Given: 빈 질문 거절

        // When & Then
        mockMvc.perform(get("/api/v1/chat")
                        .param("question", " ")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andDo(document("chat/blank-question", responseFields(errorFields())));
    }

    @Test
    @DisplayName("지원하지 않는 모델 거절")
    void rejectsUnknownModel() throws Exception {
        // Given: 지원하지 않는 모델 거절

        // When & Then
        mockMvc.perform(get("/api/v1/chat")
                        .param("question", "질문")
                        .param("model", "UNKNOWN")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andDo(document("chat/unknown-model", responseFields(errorFields())));
    }

    private static FieldDescriptor[] errorFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("오류 코드"),
            fieldWithPath("message").description("오류 메시지"),
            fieldWithPath("path").description("요청 경로"),
            fieldWithPath("requestId").optional().description("요청 추적 ID")
        };
    }
}

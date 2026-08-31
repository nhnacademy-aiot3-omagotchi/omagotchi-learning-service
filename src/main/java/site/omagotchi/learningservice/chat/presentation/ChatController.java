package site.omagotchi.learningservice.chat.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import site.omagotchi.learningservice.chat.presentation.request.ChatModelType;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient geminiChatClient;
    private final ChatClient ollamaChatClient;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            JwtAuthenticationToken authentication,
            @NotBlank @Size(max = 1000) @RequestParam String question,
            @RequestParam(defaultValue = "GEMINI") ChatModelType model
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        String conversationId = user.userId().toString();

        // 질문은 자유 텍스트라 개인정보가 섞일 수 있어 INFO에 남기지 않는다
        // Tool 호출 판단을 봐야 하는 로컬에서만 DEBUG로 확인한다 (application-local.yaml)
        // 이 스택에는 엑세스 로그, 프록시가 없어 질문이 기록되는 지점은 아래 debug 한 줄 뿐임
        // 내부 사용자 UUID는 로그에 남기지 않는다. 추적용으로 앞 8자만 남긴다
        String maskedUserId = user.userId().toString().substring(0, 8);
        log.info("[ChatController] userId(masked) = {}, model = {}", maskedUserId, model);
        log.debug("[ChatController] 질문 = {}", question);

        return this.resolveChatClient(model).prompt()
                .user(question)
                .toolContext(Map.of("userId", user.userId()))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnError(e -> log.error("[ChatController] 스트리밍 에러 - model = {}", model, e));
    }

    // 의도적으로 default를 두지 않았음. 나중에 모델을 추가하면 컴파일 에러가 나서 여기를 고치도록 강제됨
    // default를 두면 새 모델이 조용히 엉뚱한 곳으로 빠짐
    private ChatClient resolveChatClient(ChatModelType model) {
        return switch (model) {
            case GEMINI -> this.geminiChatClient;
            case OLLAMA -> this.ollamaChatClient;
        };
    }
}

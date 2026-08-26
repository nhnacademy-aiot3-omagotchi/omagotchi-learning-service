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
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient geminiChatClient;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            JwtAuthenticationToken authentication,
            @NotBlank @Size(max = 1000) @RequestParam String question
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        String conversationId = user.userId().toString();

        // TODO 사용자 question을 로깅하는 게 문제가 될지는 차후 파악이 필요함 (자유 텍스트이므로) (로그 보존 또한) (예: "우리 KDT 과정에 재민이 걔 정말 별로지 않아?")
        log.info("[ChatController] userId = {}, 질문 = {}", user.userId(), question);

        return this.geminiChatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnError(e -> log.error("[ChatController] 스트리밍 에러", e));
    }
}

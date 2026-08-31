package site.omagotchi.learningservice.chat.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("모델별 ChatClient 구성")
class ChatClientConfigTest {

    // 실제 호출은 하지 않으므로 연결 정보는 형식만 맞춘 더미 값을 쓴다
    // GoogleGenAiChatAutoConfiguration은 운영과 똑같이 쓰지 않는다. Gemini 모델은 ChatClientConfig가 직접 만든다
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(
                    ApplicationConversionService.getSharedInstance()
            ))
            .withConfiguration(AutoConfigurations.of(
                    ToolCallingAutoConfiguration.class,
                    OllamaApiAutoConfiguration.class,
                    OllamaChatAutoConfiguration.class
            ))
            .withUserConfiguration(ChatMemoryConfig.class, ChatClientConfig.class)
            .withPropertyValues(
                    "gemini.api-keys=dummy-key-1,dummy-key-2,dummy-key-3",
                    "spring.ai.google.genai.chat.model=gemini-2.5-flash",
                    "spring.ai.ollama.base-url=http://localhost:11434",
                    "spring.ai.ollama.chat.model=qwen2.5:latest"
            );

    @Test
    @DisplayName("Gemini와 Ollama ChatClient가 모두 생성된다")
    void createsBothChatClients() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context).hasBean("geminiRoundRobinChatModel");
            assertThat(context).hasBean("ollamaChatModel");

            assertThat(context.getBean("geminiChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("ollamaChatClient")).isInstanceOf(ChatClient.class);
        });
    }

    @Test
    @DisplayName("키 개수만큼 Gemini 모델을 만들어 라운드로빈으로 묶는다")
    void buildsOneModelPerApiKey() {
        this.contextRunner.run(context -> {
            RoundRobinChatModel chatModel =
                    context.getBean("geminiRoundRobinChatModel", RoundRobinChatModel.class);

            assertThat(chatModel.size()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("gemini.api-keys가 비어 있으면 Context 시작에 실패한다")
    void failsWithoutGeminiApiKeys() {
        this.contextRunner
                .withPropertyValues("gemini.api-keys=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("두 ChatClient는 서로 다른 인스턴스다")
    void chatClientsAreDistinct() {
        this.contextRunner.run(context ->
                assertThat(context.getBean("geminiChatClient"))
                        .isNotSameAs(context.getBean("ollamaChatClient"))
        );
    }

    @Test
    @DisplayName("Ollama base-url이 비어 있으면 Context 시작에 실패한다")
    void failsWithoutOllamaBaseUrl() {
        this.contextRunner
                .withPropertyValues("spring.ai.ollama.base-url=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("ChatClient가 모델에 시스템 메시지를 함께 보낸다")
    void sendsSystemMessageToModel() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();

        // ChatModel은 call(Prompt) 하나만 추상 메서드라 람다로 스텁할 수 있다
        ChatModel stubChatModel = prompt -> {
            capturedPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("테스트 응답"))));
        };

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();

        ChatClient chatClient = new ChatClientConfig().geminiChatClient(stubChatModel, List.of(), chatMemory);

        chatClient.prompt()
                .user("안녕")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "test-conversation"))
                .call()
                .content();

        List<Message> instructions = capturedPrompt.get().getInstructions();

        assertThat(instructions).hasAtLeastOneElementOfType(SystemMessage.class);
    }
}

package site.omagotchi.learningservice.chat.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("모델별 ChatClient 구성")
class ChatClientConfigTest {

    // 실제 호출은 하지 않으므로 연결 정보는 형식만 맞춘 더미 값을 쓴다
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(
                    ApplicationConversionService.getSharedInstance()
            ))
            .withConfiguration(AutoConfigurations.of(
                    ToolCallingAutoConfiguration.class,
                    GoogleGenAiChatAutoConfiguration.class,
                    OllamaApiAutoConfiguration.class,
                    OllamaChatAutoConfiguration.class
            ))
            .withUserConfiguration(ChatMemoryConfig.class, ChatClientConfig.class)
            .withPropertyValues(
                    "spring.ai.google.genai.api-key=dummy-api-key",
                    "spring.ai.google.genai.chat.model=gemini-2.5-flash",
                    "spring.ai.ollama.base-url=http://localhost:11434",
                    "spring.ai.ollama.chat.model=qwen2.5:latest"
            );

    @Test
    @DisplayName("Gemini와 Ollama ChatClient가 모두 생성된다")
    void createsBothChatClients() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context).hasBean("googleGenAiChatModel");
            assertThat(context).hasBean("ollamaChatModel");

            assertThat(context.getBean("geminiChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("ollamaChatClient")).isInstanceOf(ChatClient.class);
        });
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
}

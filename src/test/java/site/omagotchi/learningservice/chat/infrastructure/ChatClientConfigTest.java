package site.omagotchi.learningservice.chat.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.reactive.ReactiveHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
            // 대화 기록이 Redis로 옮겨가면서 ChatMemoryConfig가 이 둘을 요구한다
            // 여기서는 빈 구성만 검증하므로 Redis에 실제로 접근하지는 않는다
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "gemini.api-keys=dummy-key-1,dummy-key-2,dummy-key-3",
                    "spring.ai.google.genai.chat.model=gemini-3.6-flash",
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
    @DisplayName("OllamaApiConfig가 자동구성 대신 OllamaApi를 제공한다")
    void ollamaApiConfigReplacesAutoConfiguration() {
        this.ollamaApiConfigRunner().run(context -> {
            assertThat(context).hasNotFailed();

            // 자동구성과 우리 빈이 함께 뜨면 어느 쪽이 쓰이는지 알 수 없다
            assertThat(context).hasSingleBean(OllamaApi.class);

            // 자동구성 빈도 이름이 ollamaApi라, 이름만으로는 어느 쪽인지 구분되지 않는다
            // 정의 출처를 봐야 자동구성이 실제로 물러났음이 증명된다
            assertThat(context.getBeanFactory().getBeanDefinition("ollamaApi").getFactoryBeanName())
                    .isEqualTo("ollamaApiConfig");

            // 교체한 OllamaApi 위에서 ChatClient까지 정상적으로 만들어져야 한다
            assertThat(context.getBean("ollamaChatClient")).isInstanceOf(ChatClient.class);
        });
    }

    @Test
    @DisplayName("Ollama 타임아웃 설정이 없으면 Context 시작에 실패한다")
    void failsWithoutOllamaTimeoutProperties() {
        this.contextRunner
                .withUserConfiguration(OllamaApiConfig.class)
                .withConfiguration(AutoConfigurations.of(
                        HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class,
                        ReactiveHttpClientAutoConfiguration.class,
                        RestClientAutoConfiguration.class,
                        WebClientAutoConfiguration.class
                ))
                .run(context -> {
                    assertThat(context).hasFailed();
                    // 다른 이유로 실패해도 hasFailed()는 통과하므로 원인을 함께 고정한다
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ollama.connect-timeout");
                });
    }

    @Test
    @DisplayName("Ollama 읽기 타임아웃 설정이 없으면 Context 시작에 실패한다")
    void failsWithoutOllamaReadTimeout() {
        this.contextRunner
                .withUserConfiguration(OllamaApiConfig.class)
                .withConfiguration(AutoConfigurations.of(
                        HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class,
                        ReactiveHttpClientAutoConfiguration.class,
                        RestClientAutoConfiguration.class,
                        WebClientAutoConfiguration.class
                ))
                // 연결 타임아웃만 채워 두 값이 각각 필수임을 확인한다
                .withPropertyValues("ollama.connect-timeout=2s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ollama.read-timeout");
                });
    }

    /**
     * OllamaApiConfig는 컨텍스트의 RestClient·WebClient 빌더를 받아 쓰므로, 그 둘을 만드는 자동구성까지 함께 올려야 실제 배선과 같아진다.
     */
    private ApplicationContextRunner ollamaApiConfigRunner() {
        return this.contextRunner
                .withUserConfiguration(OllamaApiConfig.class)
                .withConfiguration(AutoConfigurations.of(
                        HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class,
                        ReactiveHttpClientAutoConfiguration.class,
                        RestClientAutoConfiguration.class,
                        WebClientAutoConfiguration.class
                ))
                .withPropertyValues(
                        "ollama.connect-timeout=2s",
                        "ollama.read-timeout=60s"
                );
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

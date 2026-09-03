package site.omagotchi.learningservice.chat.infrastructure;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.chat.application.ChatSystemPrompt;
import site.omagotchi.learningservice.global.ai.AiToolProvider;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
// spring.ai.google.genai.chat.* 를 읽어주던 프로퍼티 빈을 여기서 직접 살린다
@EnableConfigurationProperties({GoogleGenAiChatProperties.class, GeminiProperties.class})
public class ChatClientConfig {

    /**
     * 키 하나당 모델 하나를 만들어, 요청마다 번갈아 쓰는 ChatModel 하나로 묶는다
     * 옵션(model, temperature 등)은 모든 키가 동일하게 spring.ai.google.genai.chat.* 를 쓴다
     */
    @Bean
    public RoundRobinChatModel geminiRoundRobinChatModel(
            GeminiProperties geminiProperties,
            GoogleGenAiChatProperties chatProperties,
            ToolCallingManager toolCallingManager
    ) {
        List<ChatModel> models = new ArrayList<>();

        for (String apiKey : geminiProperties.apiKeys()) {
            Client genAiClient = Client.builder()
                    .apiKey(apiKey)
                    .build();

            GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                    .genAiClient(genAiClient)
                    .options(chatProperties.toOptions())
                    .toolCallingManager(toolCallingManager)
                    .build();

            models.add(model);
        }

        log.info("[ChatClientConfig] Gemini 키 {}개로 라운드로빈을 구성했습니다", models.size());

        return new RoundRobinChatModel(models);
    }

    @Bean
    public ChatClient geminiChatClient(
            ChatModel geminiRoundRobinChatModel,
            List<AiToolProvider> toolProviders,
            ChatMemory chatMemory
    ) {
        return this.buildChatClient(geminiRoundRobinChatModel, toolProviders, chatMemory);
    }

    @Bean
    public ChatClient ollamaChatClient(
            ChatModel ollamaChatModel,
            List<AiToolProvider> toolProviders,
            ChatMemory chatMemory
    ) {
        return this.buildChatClient(ollamaChatModel, toolProviders, chatMemory);
    }

    /**
     * 모델만 다르고 Tool과 대화기록은 동일하게 구성한다
     * 같은 ChatMemory를 공유하므로 사용자가 대화 중 모델을 바꿔도 앞의 맥락이 이어진다
     */
    private ChatClient buildChatClient(
            ChatModel chatModel,
            List<AiToolProvider> toolProviders,
            ChatMemory chatMemory
    ) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ChatSystemPrompt.DEFAULT)
                .defaultTools(toolProviders.toArray())
                // .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))로 대화방 ID 넘겨줘야 대화방별로 기억이 구분됨
                // MessageChatMemoryAdvisor는 모델 호출 전에 질문을 저장해서, 실패하면 답 없는 질문이 남고 다음 턴에서 모델이 그것까지 답해 버리므로, 새로 구현하여 씀
                .defaultAdvisors(new CompletedTurnChatMemoryAdvisor(chatMemory))
                .build();
    }
}

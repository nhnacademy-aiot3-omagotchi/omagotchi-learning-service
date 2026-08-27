package site.omagotchi.learningservice.chat.infrastructure;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.chat.application.ChatSystemPrompt;

import java.util.List;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient geminiChatClient(
            ChatModel googleGenAiChatModel,
            List<AiToolProvider> toolProviders,
            ChatMemory chatMemory
    ) {
        return this.buildChatClient(googleGenAiChatModel, toolProviders, chatMemory);
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
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}

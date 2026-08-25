package site.omagotchi.learningservice.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.global.ai.AiToolProvider;

import java.util.List;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient geminiChatClient(
            @Qualifier("googleGenAiChatModel") ChatModel chatModel,
            List<AiToolProvider> toolProviders,
            ChatMemory chatMemory
    ) {
        return ChatClient.builder(chatModel)
                .defaultTools(toolProviders.toArray())
                // .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))로 대화방 ID 넘겨줘야 대화방별로 기억이 구분됨
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}

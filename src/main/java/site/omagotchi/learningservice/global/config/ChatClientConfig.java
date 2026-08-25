package site.omagotchi.learningservice.global.config;

import org.springframework.ai.chat.client.ChatClient;
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
            List<AiToolProvider> toolProviders
    ) {
        return ChatClient.builder(chatModel)
                .defaultTools(toolProviders.toArray())
                .build();
    }
}

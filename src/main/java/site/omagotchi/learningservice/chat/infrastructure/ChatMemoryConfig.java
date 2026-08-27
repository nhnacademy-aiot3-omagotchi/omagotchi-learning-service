package site.omagotchi.learningservice.chat.infrastructure;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new CaffeineChatMemoryRepository()) // 마지막 대화 후 1시간 지나면 자동 삭제
                .maxMessages(10) // 일단 10으로 하였음. 나중에 필요하면 조정
                .build();
    }
}

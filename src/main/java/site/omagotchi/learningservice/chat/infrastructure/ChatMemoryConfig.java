package site.omagotchi.learningservice.chat.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return MessageWindowChatMemory.builder()
                // 마지막 대화 후 1시간 지나면 자동 삭제. 인스턴스 간 공유를 위해 Redis에 둔다
                .chatMemoryRepository(new RedisChatMemoryRepository(redisTemplate, objectMapper))
                .maxMessages(10) // 일단 10으로 하였음. 나중에 필요하면 조정
                .build();
    }
}

package site.omagotchi.learningservice.chat.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 대화 기록을 Redis에 둔다
 * 인스턴스가 여러 대로 늘어나면 Caffeine 캐시는 인스턴스마다 따로 있어 같은 사용자가 다른 인스턴스로 라우팅될 때 맥락이 끊긴다.
 * 그래서 인스턴스 밖의 공용 저장소로 옮겼다
 * Message는 인터페이스라 그대로 직렬화할 수 없어, 타입을 필드로 들고 다니는 형태로 바꿔 JSON으로 저장한다
 * metadata(모델이 돌려주는 finishReason 등)는 담지 않는다 (대화 맥락 유지에 쓰이지 않고, 모델마다 담기는 타입이 달라 직렬화가 깨지기 쉽다)
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "omagotchi:chat:memory:";
    private static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration timeToLive;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_TIME_TO_LIVE);
    }

    // 만료 동작을 검증할 때 짧은 TTL을 넣을 수 있게 열어둔다 (운영에서는 기본 생성자만 씀)
    RedisChatMemoryRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Duration timeToLive) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.timeToLive = timeToLive;
    }

    /**
     * 지금 저장된 대화방 전부 알려줘 -> 빈 목록만 리턴함
     * 우리 Redis는 재실 상태·센서 이벤트와 함께 쓰므로 대화 키만 골라내려면 전체를 훑어야 하고, 그동안 다른 요청이 막힌다
     * 우리 서비스는 이 기능을 쓰지 않는데(인터페이스에 있어서 오버라이딩만 해 놓은것) 위험한 코드를 넣을 이유가 없어서 비워두었음
     */

    /**
     * 대화 ID 전체 조회는 현재 지원 X (따라서 List.of() 리턴)
     * 우리 서비스는 ChatMemoryRepository의 조회/저장/삭제 만 사용하며, 대화 목록 조회 기능이나 호출처가 없음
     * 차후에 목록 조회가 필요해지면 호출 빈도, Redis 키 규모, TTL 정합성을 고려하여 SCAN 또는 전용 인덱스 방식으로 별도 설계하려고 한다.
     */
    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    // 저장의 역순 (글자 꺼내서 -> 객체로 되돌리고 -> Message로 복원)
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String json = this.redisTemplate.opsForValue().get(key(conversationId));

        if (Objects.isNull(json)) {
            return List.of();
        }

        try {
            List<StoredMessage> stored = this.objectMapper.readValue(json, new TypeReference<>() {
            });

            return stored.stream()
                    .map(this::toMessage)
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // 저장된 글자가 깨져 있으면(저장 형식을 바꿨거나 값이 손상됐거나) 여기서 예외가 남
            // -> 예외 안 던지고 빈 목록 리턴함(대화를 막지 않고 맥락 없이 새로 시작한다)
            // 이전 대화를 못 읽는 것과 채팅을 아예 못 하는 것은 다르기 때문 (맥락은 잃지만 새 대화는 시작할 수 있게 두는 것)
            log.warn("[RedisChatMemoryRepository] 대화 기록을 읽지 못해 비운 채로 진행합니다", e);
            return List.of();
        }
    }

    /**
     * 매 대화마다 호출되며, 이때 TTL도 함께 갱신된다
     * 한 요청에서 조회와 저장이 쌍으로 일어나므로 저장 시점에만 갱신해도 계속 대화하는 사용자의 기록은 지워지지 않는다
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            List<StoredMessage> stored = messages.stream()
                    .map(this::toStored) // Message 객체들을 저장하기 좋은 형태(StoredMessage)로 바꿈
                    .toList();

            this.redisTemplate.opsForValue()
                    // writeValueAsString(stored): 그걸 JSON 글자로 바꿈
                    // set(키, 글자, 1시간): Redis에 넣으면서 동시에 1시간 뒤에 지우라고 함께 지정
                    .set(key(conversationId), this.objectMapper.writeValueAsString(stored), this.timeToLive);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대화 기록을 저장할 수 없습니다.", e);
        }
    }

    // 그냥 지운다
    // 현재 우리 서비스에서는 호출하는 곳이 없고, 나중에 대화 기록 삭제 버튼 같은 것이 생기면 쓰게 될 것임
    @Override
    public void deleteByConversationId(String conversationId) {
        this.redisTemplate.delete(key(conversationId));
    }

    // 키 만들기
    private String key(String conversationId) {

        // omagotchi:chat:memory:abc-123
        return KEY_PREFIX + conversationId;
    }

    // Message <-> 저장 형식

    private StoredMessage toStored(Message message) {
        List<StoredToolCall> toolCalls = new ArrayList<>();
        List<StoredToolResponse> toolResponses = new ArrayList<>();

        if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            assistantMessage.getToolCalls().forEach(toolCall -> toolCalls.add(
                    new StoredToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments())));
        }

        if (message instanceof ToolResponseMessage toolResponseMessage) {
            toolResponseMessage.getResponses().forEach(response -> toolResponses.add(
                    new StoredToolResponse(response.id(), response.name(), response.responseData())));
        }

        return new StoredMessage(
                message.getMessageType().name(),
                message.getText(),
                toolCalls,
                toolResponses
        );
    }

    private Message toMessage(StoredMessage stored) {
        return switch (MessageType.valueOf(stored.type())) {
            case SYSTEM -> new SystemMessage(stored.text());
            case USER -> new UserMessage(stored.text());
            case ASSISTANT -> AssistantMessage.builder()
                    .content(stored.text())
                    .toolCalls(stored.toolCalls().stream()
                            .map(toolCall -> new AssistantMessage.ToolCall(
                                    toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                            .toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(stored.toolResponses().stream()
                            .map(response -> new ToolResponseMessage.ToolResponse(
                                    response.id(), response.name(), response.responseData()))
                            .toList())
                    .build();
        };
    }

    // Message가 인터페이스라 JSON만으로는 어느 구현체인지 알 수 없어 type을 직접 들고 다닌다
    record StoredMessage(
            String type,
            String text,
            List<StoredToolCall> toolCalls,
            List<StoredToolResponse> toolResponses
    ) {
    }

    record StoredToolCall(String id, String type, String name, String arguments) {
    }

    record StoredToolResponse(String id, String name, String responseData) {
    }
}

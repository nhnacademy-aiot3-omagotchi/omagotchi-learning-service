package site.omagotchi.learningservice.chat.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Caffeine에서 Redis로 옮기면서 Message를 직렬화하게 되었다
 * 저장했다 되살렸을 때 대화 맥락이 손실되지 않는지, TTL이 매 저장마다 갱신되는지 고정한다
 */
@DisplayName("Redis 대화 기록 저장소")
class RedisChatMemoryRepositoryTest {

    private static final String CONVERSATION_ID = "00000000-0000-0000-0000-000000000001";
    private static final String KEY = "omagotchi:chat:memory:" + CONVERSATION_ID;
    private static final Duration TTL = Duration.ofHours(1);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisChatMemoryRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        repository = new RedisChatMemoryRepository(redisTemplate, new ObjectMapper(), TTL);
    }

    @Nested
    @DisplayName("저장")
    class Save {

        @Test
        @DisplayName("대화 기록을 TTL과 함께 저장한다")
        void savesWithTimeToLive() {
            repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("안녕")));

            verify(valueOperations).set(eq(KEY), any(String.class), eq(TTL));
        }

        @Test
        @DisplayName("저장할 때마다 TTL이 갱신되어 계속 대화하는 사용자의 기록은 지워지지 않는다")
        void refreshesTimeToLiveOnEverySave() {
            repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("첫 질문")));
            repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("두 번째 질문")));

            verify(valueOperations, times(2)).set(eq(KEY), any(String.class), eq(TTL));
        }
    }

    @Nested
    @DisplayName("조회")
    class Find {

        @Test
        @DisplayName("저장한 대화를 그대로 되살린다")
        void restoresSavedConversation() {
            givenStored(List.of(
                    new SystemMessage("당신은 오마고치의 AI 도우미입니다."),
                    new UserMessage("내 공부 습관 어때?"),
                    new AssistantMessage("몰입 밀도가 62%로 낮은 편입니다.")
            ));

            List<Message> restored = repository.findByConversationId(CONVERSATION_ID);

            assertThat(restored).hasSize(3);
            assertThat(restored).extracting(Message::getText)
                    .containsExactly(
                            "당신은 오마고치의 AI 도우미입니다.",
                            "내 공부 습관 어때?",
                            "몰입 밀도가 62%로 낮은 편입니다.");
            assertThat(restored).extracting(Message::getMessageType)
                    .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
        }

        @Test
        @DisplayName("Tool 호출과 실행 결과도 손실 없이 되살린다")
        void restoresToolCallMessages() {
            givenStored(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "call_1", "function", "getStudyPattern", "{\"periodDays\":30}")))
                            .build(),
                    ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(
                                    "call_1", "getStudyPattern", "{\"status\":\"OK\"}")))
                            .build()
            ));

            List<Message> restored = repository.findByConversationId(CONVERSATION_ID);

            AssistantMessage assistantMessage = (AssistantMessage) restored.get(0);
            assertThat(assistantMessage.getToolCalls()).hasSize(1);
            assertThat(assistantMessage.getToolCalls().getFirst().name()).isEqualTo("getStudyPattern");
            assertThat(assistantMessage.getToolCalls().getFirst().arguments()).isEqualTo("{\"periodDays\":30}");

            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) restored.get(1);
            assertThat(toolResponseMessage.getResponses()).hasSize(1);
            assertThat(toolResponseMessage.getResponses().getFirst().responseData())
                    .isEqualTo("{\"status\":\"OK\"}");
        }

        @Test
        @DisplayName("기록이 없으면 빈 목록을 돌려준다")
        void returnsEmptyWhenAbsent() {
            given(valueOperations.get(KEY)).willReturn(null);

            assertThat(repository.findByConversationId(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("저장된 값이 깨져 있어도 대화를 막지 않고 빈 목록으로 시작한다")
        void returnsEmptyWhenStoredValueIsBroken() {
            given(valueOperations.get(KEY)).willReturn("{이건 JSON이 아니다");

            assertThat(repository.findByConversationId(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("JSON은 멀쩡해도 Message로 되돌릴 수 없으면 빈 목록으로 시작한다")
        void returnsEmptyWhenStoredValueCannotBeRestored() {
            // 저장 형식을 바꾸면 이전 값에 이런 상태가 남는다. 여기서 예외를 던지면
            // 저장까지 가지 못해 깨진 값이 그대로 남고, 사용자는 TTL이 끝날 때까지 채팅을 못 한다
            given(valueOperations.get(KEY)).willReturn(
                    "[{\"type\":\"UNKNOWN_KIND\",\"text\":\"안녕\",\"toolCalls\":[],\"toolResponses\":[]}]");

            assertThat(repository.findByConversationId(CONVERSATION_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("그 외 계약")
    class Contract {

        @Test
        @DisplayName("삭제하면 키를 지운다")
        void deletesKey() {
            repository.deleteByConversationId(CONVERSATION_ID);

            verify(redisTemplate).delete(KEY);
        }

        @Test
        @DisplayName("대화 목록 조회는 지원하지 않아 빈 목록을 돌려준다")
        void doesNotSupportListingConversations() {
            // 전체 키 스캔은 같은 Redis를 쓰는 다른 기능까지 막으므로 구현하지 않는다
            assertThat(repository.findConversationIds()).isEmpty();
        }
    }

    /** saveAll이 만든 JSON을 그대로 조회 결과로 돌려주도록 스텁한다. */
    private void givenStored(List<Message> messages) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        repository.saveAll(CONVERSATION_ID, messages);
        verify(valueOperations).set(eq(KEY), captor.capture(), eq(TTL));
        given(valueOperations.get(KEY)).willReturn(captor.getValue());
    }
}

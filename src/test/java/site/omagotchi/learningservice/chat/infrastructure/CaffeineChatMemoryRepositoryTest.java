package site.omagotchi.learningservice.chat.infrastructure;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("대화 기록 보관과 자동 만료")
class CaffeineChatMemoryRepositoryTest {

    private static final Duration TIME_TO_LIVE = Duration.ofHours(1);
    private static final String CONVERSATION_ID = "019d2a48-80c0-4d6a-9a15-0b16d2dd74f1";

    private FakeTicker ticker;
    private CaffeineChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        this.ticker = new FakeTicker();
        this.repository = new CaffeineChatMemoryRepository(TIME_TO_LIVE, this.ticker);
    }

    @Test
    @DisplayName("저장한 대화를 그대로 조회한다")
    void savesAndFindsMessages() {
        List<Message> messages = List.of(
                new UserMessage("광주 동구 날씨 알려줘"),
                new AssistantMessage("흐리고 33도입니다.")
        );

        this.repository.saveAll(CONVERSATION_ID, messages);

        assertThat(this.repository.findByConversationId(CONVERSATION_ID)).isEqualTo(messages);
    }

    @Test
    @DisplayName("저장된 적 없는 대화는 빈 리스트를 반환한다")
    void returnsEmptyListForUnknownConversation() {
        assertThat(this.repository.findByConversationId("없는대화")).isEmpty();
    }

    @Test
    @DisplayName("마지막 사용 후 보관 기간이 지나면 대화가 사라진다")
    void expiresAfterTimeToLive() {
        this.repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("안녕")));

        this.ticker.advance(TIME_TO_LIVE.plusMinutes(1));

        assertThat(this.repository.findByConversationId(CONVERSATION_ID)).isEmpty();
    }

    @Test
    @DisplayName("보관 기간이 지나기 전에는 대화가 유지된다")
    void keepsConversationBeforeTimeToLive() {
        this.repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("안녕")));

        this.ticker.advance(TIME_TO_LIVE.minusMinutes(1));

        assertThat(this.repository.findByConversationId(CONVERSATION_ID)).hasSize(1);
    }

    @Test
    @DisplayName("계속 대화하면 만료 시점이 갱신되어 사라지지 않는다")
    void accessRefreshesExpiration() {
        this.repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("안녕")));

        // 만료 직전에 다시 사용하는 것을 세 번 반복한다.
        // 만료 시점이 갱신되지 않는다면 이 시점엔 이미 사라져 있어야 한다.
        for (int i = 0; i < 3; i++) {
            this.ticker.advance(TIME_TO_LIVE.minusMinutes(1));
            assertThat(this.repository.findByConversationId(CONVERSATION_ID)).hasSize(1);
        }
    }

    @Test
    @DisplayName("대화를 삭제하면 즉시 조회되지 않는다")
    void deletesConversation() {
        this.repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("안녕")));

        this.repository.deleteByConversationId(CONVERSATION_ID);

        assertThat(this.repository.findByConversationId(CONVERSATION_ID)).isEmpty();
    }

    @Test
    @DisplayName("보관 중인 대화 ID 목록을 조회한다")
    void findsConversationIds() {
        this.repository.saveAll("사용자1", List.of(new UserMessage("안녕")));
        this.repository.saveAll("사용자2", List.of(new UserMessage("반가워")));

        assertThat(this.repository.findConversationIds()).containsExactlyInAnyOrder("사용자1", "사용자2");
    }

    // 실제로 1시간을 기다릴 수 없으니 시간을 흘려보냄
    private static final class FakeTicker implements Ticker {
        private long nanos = 0L;

        @Override
        public long read() {
            return this.nanos;
        }

        void advance(Duration duration) {
            this.nanos += duration.toNanos();
        }
    }
}

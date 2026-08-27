package site.omagotchi.learningservice.chat.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * ChatMemoryRepository: Spring AI가 정의해둔 인터페이스 (대화 내용을 어딘가에 저장/조회/삭제하는 방법을 4개 메서드로 규정해놓은 계약)
 */
public class CaffeineChatMemoryRepository implements ChatMemoryRepository {

    private static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofHours(1);

    // Key는 conversationId (우리에게는 userId)
    // Value는 그 유저의 대화 메시지 목록
    private final Cache<String, List<Message>> cache;

    public CaffeineChatMemoryRepository() {
        this(DEFAULT_TIME_TO_LIVE, Ticker.systemTicker());
    }

    // 만료 동작을 검증하려면 시간을 흘려보낼 수 있어야 해서 Ticker를 받는 생성자를 둠 (운영에서는 기본생성자만 씀)
    // Ticker는 카페인 버전의 Clock임
    CaffeineChatMemoryRepository(Duration timeToLive, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                // 마지막으로 이 Key를 읽거나 쓴 시점부터 1시간이 지나면 자동으로 삭제
                // expireAfterWrite(쓴 시점 기준)와 다르게, 계속 대화를 하는 유저는 매번 접근이 갱신돼서 안 지워짐 (1시간동안 조용한 유저만 지워짐)
                .expireAfterAccess(timeToLive)
                .ticker(ticker)
                .build();
    }

    // 캐시 안에 지금 살아있는 모든 유저 ID 목록 반환
    // cache.asMap()은 캐시 내용을 일반 Map처럼 보여주는 뷰 -> 거기서 keySet()을 뽑아 리스트로 복사
    // (우리 애플리케이션에서 쓰지는 않지만 인터페이스 계약이라 구현은 해야 함)
    @Override
    public List<String> findConversationIds() {
        return List.copyOf(this.cache.asMap().keySet());
    }

    // 이 유저의 대화 기록을 꺼냄
    // getIfPresent는 캐시에 없으면 null을 리턴함 -> 호출하는 쪽(MessageWindowChatMemory)이 null을 못 받게 돼 있어서 빈 리스트로 바꿔서 리턴
    // 우리 애플리케이션에서 매 채팅 요청마다 호출되는 부분 (이전 대화 기록을 불러오는 자리라서)
    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> messages = this.cache.getIfPresent(conversationId);

        return Objects.nonNull(messages)
                ? messages
                : List.of();
    }

    // 대화가 끝날 때마다 그 유저의 최신 메시지 목록(10개로 잘린 상태)을 캐시에 덮어씀
    // 이게 호출될 때마다 카페인 내부적으로 마지막 접근 시각이 갱신돼서, 계속 대화하는 유저는 타이머가 계속 리셋됨
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        this.cache.put(conversationId, messages);
    }

    // 특정 유저의 대화 기록을 즉시 삭제
    // 우리 애플리케이션은 이걸 직접 호출하는 곳이 없지만(로그아웃 이벤트가 없어서) 나중에 대화 기록 삭제 버튼 같은 게 생기면 이 메서드 쓰면 됨
    @Override
    public void deleteByConversationId(String conversationId) {
        this.cache.invalidate(conversationId);
    }
}

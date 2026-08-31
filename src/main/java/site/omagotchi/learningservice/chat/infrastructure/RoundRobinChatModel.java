package site.omagotchi.learningservice.chat.infrastructure;

import com.google.genai.errors.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 키만 다른 Gemini 모델 여러 개를 요청마다 번갈아 쓰는 ChatModel
 *
 * 대화 기록은 우리 서버(ChatMemory)에 있어서 키가 바뀌어도 맥락은 그대로 이어진다
 */
@Slf4j
public class RoundRobinChatModel implements ChatModel {

    private static final int QUOTA_EXCEEDED_STATUS = 429;

    // nextIndex()가 "지금 쓸 수 있는 키가 하나도 없다"를 알리는 값
    private static final int NO_AVAILABLE_KEY = -1;

    // 분당 요청 한도에 맞춘 값. 하루 한도가 바닥난 키는 60초 뒤 한 번 더 맞고 다시 쉰다
    private static final long COOLDOWN_MILLIS = 60_000L;

    private final List<ChatModel> delegates;

    // 요청을 여러 스레드가 동시에 처리하므로 int++로는 순번이 겹친다
    private final AtomicInteger counter = new AtomicInteger();

    // (index + 시각) 키를 언제까지 쉬게 할지
    private final AtomicLongArray blockedUntil;

    public RoundRobinChatModel(List<ChatModel> delegates) {
        if (Objects.isNull(delegates) || delegates.isEmpty()) {
            throw new IllegalArgumentException("라운드로빈으로 돌릴 모델이 하나도 없습니다.");
        }

        this.delegates = List.copyOf(delegates);
        this.blockedUntil = new AtomicLongArray(this.delegates.size());
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RuntimeException lastError = null;

        for (int attempt = 0; attempt < this.delegates.size(); attempt++) {
            int index = this.nextIndex();                           // ① 쓸 키를 고른다

            if (index == NO_AVAILABLE_KEY) {
                break;
            }

            try {
                return this.delegates.get(index).call(prompt);      // ② 그 키로 물어본다. 성공하면 여기서 끝
            } catch (RuntimeException e) {
                if (!this.markIfQuotaExceeded(index, e)) {
                    throw e;                                        // ③ 할당량 문제가 아니면 포기
                }

                lastError = e;                                      // ④ 할당량 문제면 기억해두고 다음 키로
                log.warn("[RoundRobinChatModel] {}번 키 할당량 초과. 다음 키로 재시도합니다", index);
            }
        }

        if (Objects.nonNull(lastError)) {
            throw lastError;                                        // ⑤ 이번 요청에서 429를 맞았다면 그 원인을 그대로 전달
        }

        throw this.allKeysExhausted();                              // ⑥ 한 번도 호출하지 못한 경우 (전부 쿨다운 중)
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        int index = this.nextIndex();

        if (index == NO_AVAILABLE_KEY) {
            return Flux.error(this.allKeysExhausted());
        }

        return this.streamWithFailover(prompt, 0, index);
    }

    /**
     * 지우면 Tool 호출이 조용히 죽는다
     * ChatClient는 요청을 만들 때 chatModel.getOptions()를 복사해서 쓰는데,
     * 오버라이드하지 않으면 인터페이스 기본 구현이 '빈 ChatOptions'를 돌려준다
     * 그러면 모델 이름과 temperature가 사라지는 것은 물론, ToolCallingChatOptions가 아니게 되어
     * defaultTools와 toolContext(userId)가 요청에 실리지 않는다
     */
    @Override
    public ChatOptions getOptions() {
        // 모델들은 같은 프로퍼티로 만들어서 옵션이 전부 같다. 첫 번째 것을 그대로 준다
        return this.delegates.get(0).getOptions();
    }

    /**
     * 라운드로빈에 물린 키 개수. 설정이 제대로 읽혔는지 확인하는 용도
     */
    public int size() {
        return this.delegates.size();
    }

    /**
     * 모든 키가 쿨다운이라 호출조차 하지 못한 경우의 예외
     * 원인이 된 429는 이전 요청에서 발생했으므로 여기서 넘겨줄 cause가 없다
     */
    private IllegalStateException allKeysExhausted() {
        log.error("[RoundRobinChatModel] Gemini 키 {}개가 모두 쿨다운 상태입니다. 호출하지 않고 즉시 실패시킵니다",
                this.delegates.size());

        return new IllegalStateException("Gemini 키가 모두 할당량 초과 상태입니다.");
    }

    /**
     * attempt는 지금까지 몇 개의 키로 시도했는지. 키 개수를 넘으면 포기한다
     */
    private Flux<ChatResponse> streamWithFailover(Prompt prompt, int attempt, int index) {

        // 이 호출이 사용자에게 조각을 이미 내보냈는지 표시한다
        AtomicBoolean emitted = new AtomicBoolean(false);

        return this.delegates.get(index).stream(prompt)
                .doOnNext(response -> emitted.set(true))
                .onErrorResume(error -> this.failover(prompt, attempt, index, emitted.get(), error));
    }

    private Flux<ChatResponse> failover(Prompt prompt, int attempt, int index, boolean alreadyEmitted, Throwable error) {
        // 429가 아닌가?
        if (!this.markIfQuotaExceeded(index, error)) {
            return Flux.error(error);
        }

        // 이미 글자가 나갔나?
        if (alreadyEmitted) {
            log.error("[RoundRobinChatModel] {}번 키가 응답 도중 끊겼습니다. 중복 출력을 막기 위해 재시도하지 않습니다", index);

            return Flux.error(error);
        }

        // 키를 다 써봤나?
        if (attempt + 1 >= this.delegates.size()) {
            log.error("[RoundRobinChatModel] 키 {}개를 모두 시도했지만 전부 할당량 초과입니다", this.delegates.size());

            return Flux.error(error);
        }

        int nextIndex = this.nextIndex();

        if (nextIndex == NO_AVAILABLE_KEY) {
            log.error("[RoundRobinChatModel] 남은 Gemini 키가 없습니다");

            return Flux.error(error);
        }

        log.warn("[RoundRobinChatModel] {}번 키 할당량 초과. 다음 키로 재시도합니다 ({}번째 시도)", index, attempt + 2);

        return this.streamWithFailover(prompt, attempt + 1, nextIndex);
    }

    /**
     * 쉬는 중이 아닌 키를 0, 1, 2 ... 순서로 고른다
     * floorMod를 쓰는 이유: 카운터가 int 최대값을 넘으면 음수가 되는데, 자바에서 음수 %는 음수라 인덱스로 못 쓴다
     */
    private int nextIndex() {
        long now = System.currentTimeMillis();

        for (int tried = 0; tried < this.delegates.size(); tried++) {
            int index = Math.floorMod(this.counter.getAndIncrement(), this.delegates.size());

            if (this.blockedUntil.get(index) <= now) {
                return index;                               // 쉬는 중이 아니면 사용
            }
        }

        return NO_AVAILABLE_KEY;
    }

    /**
     * 429(할당량 초과)면 그 키를 잠시 쉬게 하고 true를 돌려준다
     * 스프링 AI가 예외를 RuntimeException으로 한 번 감싸 던지므로 getCause()를 타고 내려가며 찾아야 한다
     */
    private boolean markIfQuotaExceeded(int index, Throwable error) {
        Throwable current = error;

        while (Objects.nonNull(current)) {
            if (current instanceof ApiException apiException && apiException.code() == QUOTA_EXCEEDED_STATUS) {
                this.blockedUntil.set(index, System.currentTimeMillis() + COOLDOWN_MILLIS);

                return true;
            }

            Throwable cause = current.getCause();

            if (cause == current) { // 자기 자신을 원인으로 갖는 예외가 드물게 있다. 무한 루프 방지
                return false;
            }

            current = cause;
        }

        return false;
    }
}

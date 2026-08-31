package site.omagotchi.learningservice.chat.infrastructure;

import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Gemini 키 라운드로빈")
class RoundRobinChatModelTest {

    // 스프링 AI는 구글 SDK 예외를 RuntimeException으로 한 번 감싸서 던진다. 그 모양을 그대로 흉내낸다
    private static RuntimeException quotaExceeded() {
        return new RuntimeException(
                "Failed to generate content",
                new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded")
        );
    }

    @Test
    @DisplayName("호출할 때마다 키를 순서대로 돌아가며 쓴다")
    void rotatesKeysInOrder() {
        StubChatModel first = StubChatModel.answering("1번");
        StubChatModel second = StubChatModel.answering("2번");
        StubChatModel third = StubChatModel.answering("3번");

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(first, second, third));

        List<String> answers = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            answers.add(chatModel.call(new Prompt("질문")).getResult().getOutput().getText());
        }

        assertThat(answers).containsExactly("1번", "2번", "3번", "1번", "2번", "3번");
        assertThat(first.callCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("429가 나면 다음 키로 재시도해서 답을 만들어낸다")
    void failsOverToNextKeyOnQuotaExceeded() {
        StubChatModel exhausted = StubChatModel.failing(quotaExceeded());
        StubChatModel healthy = StubChatModel.answering("정상 응답");

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(exhausted, healthy));

        String answer = chatModel.call(new Prompt("질문")).getResult().getOutput().getText();

        assertThat(answer).isEqualTo("정상 응답");
        assertThat(exhausted.callCount()).isEqualTo(1);
        assertThat(healthy.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 번 429가 난 키는 쿨다운 동안 아예 건너뛴다")
    void skipsExhaustedKey() {
        StubChatModel exhausted = StubChatModel.failing(quotaExceeded());
        StubChatModel healthy = StubChatModel.answering("정상 응답");

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(exhausted, healthy));

        chatModel.call(new Prompt("첫 질문"));
        chatModel.call(new Prompt("두 번째 질문"));

        // 두 번째 질문은 소진된 키를 건너뛰므로 호출 횟수가 늘지 않는다
        assertThat(exhausted.callCount()).isEqualTo(1);
        assertThat(healthy.callCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("스트리밍이 조각을 이미 내보낸 뒤 실패하면 재시도하지 않는다")
    void doesNotRetryAfterFirstChunk() {
        StubChatModel broken = StubChatModel.failingAfterFirstChunk("앞부분", quotaExceeded());
        StubChatModel healthy = StubChatModel.answering("전체 답변");

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(broken, healthy));

        assertThatThrownBy(() -> chatModel.stream(new Prompt("질문")).collectList().block())
                .hasRootCauseInstanceOf(ClientException.class);

        // 다른 키로 다시 만들면 "앞부분"이 두 번 나가므로 재시도하면 안 된다
        assertThat(healthy.callCount()).isZero();
    }

    @Test
    @DisplayName("아무 조각도 못 내보낸 스트리밍은 다음 키로 재시도한다")
    void retriesStreamWhenNothingEmitted() {
        StubChatModel exhausted = StubChatModel.failing(quotaExceeded());
        StubChatModel healthy = StubChatModel.answering("정상 응답");

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(exhausted, healthy));

        List<ChatResponse> responses = chatModel.stream(new Prompt("질문")).collectList().block();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getResult().getOutput().getText()).isEqualTo("정상 응답");
    }

    @Test
    @DisplayName("429가 아닌 예외는 다른 키로 재시도하지 않는다")
    void doesNotRetryOnNonQuotaError() {
        StubChatModel broken = StubChatModel.failing(new RuntimeException("네트워크 오류"));
        StubChatModel healthy = StubChatModel.answering("정상 응답");

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(broken, healthy));

        assertThatThrownBy(() -> chatModel.call(new Prompt("질문")))
                .hasMessage("네트워크 오류");

        assertThat(healthy.callCount()).isZero();
    }

    @Test
    @DisplayName("모든 키가 429면 예외를 그대로 던진다")
    void failsWhenEveryKeyIsExhausted() {
        StubChatModel first = StubChatModel.failing(quotaExceeded());
        StubChatModel second = StubChatModel.failing(quotaExceeded());

        RoundRobinChatModel chatModel = new RoundRobinChatModel(List.of(first, second));

        assertThatThrownBy(() -> chatModel.call(new Prompt("질문")))
                .hasRootCauseInstanceOf(ClientException.class);

        assertThat(first.callCount()).isEqualTo(1);
        assertThat(second.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getOptions는 첫 번째 모델의 옵션을 그대로 돌려준다")
    void delegatesOptions() {
        RoundRobinChatModel chatModel = new RoundRobinChatModel(
                List.of(StubChatModel.answering("1번"), StubChatModel.answering("2번"))
        );

        // 이게 깨지면 Tool과 모델 이름이 요청에 실리지 않는다
        assertThat(chatModel.getOptions().getModel()).isEqualTo("stub-model");
    }

    @Test
    @DisplayName("모델이 하나도 없으면 만들 수 없다")
    void rejectsEmptyDelegates() {
        assertThatThrownBy(() -> new RoundRobinChatModel(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 호출 횟수를 세어야 해서 람다 대신 클래스로 둔다
     */
    private static final class StubChatModel implements ChatModel {

        private final String answer;
        private final RuntimeException error;
        private final boolean emitBeforeError;

        private int callCount = 0;

        private StubChatModel(String answer, RuntimeException error, boolean emitBeforeError) {
            this.answer = answer;
            this.error = error;
            this.emitBeforeError = emitBeforeError;
        }

        static StubChatModel answering(String answer) {
            return new StubChatModel(answer, null, false);
        }

        static StubChatModel failing(RuntimeException error) {
            return new StubChatModel("쓰이지 않음", error, false);
        }

        static StubChatModel failingAfterFirstChunk(String firstChunk, RuntimeException error) {
            return new StubChatModel(firstChunk, error, true);
        }

        int callCount() {
            return this.callCount;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.callCount++;

            if (Objects.nonNull(this.error)) {
                throw this.error;
            }

            return this.toResponse(this.answer);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.callCount++;

            if (Objects.isNull(this.error)) {
                return Flux.just(this.toResponse(this.answer));
            }

            if (this.emitBeforeError) {
                return Flux.just(this.toResponse(this.answer)).concatWith(Flux.error(this.error));
            }

            return Flux.error(this.error);
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().model("stub-model").build();
        }

        private ChatResponse toResponse(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }
}

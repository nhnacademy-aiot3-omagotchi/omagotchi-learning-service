package site.omagotchi.learningservice.chat.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실패한 턴이 대화 기록에 남으면, 다음 턴에서 모델이 그것을 아직 답하지 않은 질문으로 보고 마저 답해 버린다.
 * 운영에서 Ollama로 물어 실패한 질문을 Gemini가 떠안는 것으로 나타났다.
 * <p>
 * 모델이 실제로 무엇을 받았는지는 프롬프트로만 확인할 수 있으므로, 스텁 모델이 받은 프롬프트를 붙잡아 검증한다.
 */
@DisplayName("정상 완료된 대화만 기록하는 Advisor")
class CompletedTurnChatMemoryAdvisorTest {

    private static final String CONVERSATION_ID = "user-1";

    private ChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        // 저장소는 검증 대상이 아니므로 인메모리로 둔다. 저장 시점만 본다
        chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Nested
    @DisplayName("실패한 턴")
    class FailedTurn {

        @Test
        @DisplayName("실패하면 그 질문은 기록에 남지 않는다")
        void doesNotKeepQuestionWhenCallFails() {
            ChatClient failing = clientOf(prompt -> {
                throw new IllegalStateException("모델 호출 실패");
            });

            assertThatThrownBy(() -> ask(failing, "오늘 광주 동구 날씨 알려줘"))
                    .isInstanceOf(RuntimeException.class);

            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("실패해도 그 전까지의 정상 대화는 유지된다")
        void keepsEarlierSuccessfulTurns() {
            ask(clientOf(answering("안녕하세요")), "안녕 넌 누구니?");

            ChatClient failing = clientOf(prompt -> {
                throw new IllegalStateException("모델 호출 실패");
            });
            assertThatThrownBy(() -> ask(failing, "오늘 광주 동구 날씨 알려줘"))
                    .isInstanceOf(RuntimeException.class);

            assertThat(texts(chatMemory.get(CONVERSATION_ID)))
                    .containsExactly("안녕 넌 누구니?", "안녕하세요");
        }

        @Test
        @DisplayName("실패한 질문은 다음 턴의 프롬프트에 실리지 않는다")
        void failedQuestionDoesNotReachNextTurn() {
            // 운영에서 관찰된 순서 그대로: 성공 → 실패 → 성공
            ask(clientOf(answering("저는 오마고치입니다")), "안녕 넌 누구니?");

            ChatClient failing = clientOf(prompt -> {
                throw new IllegalStateException("모델 호출 실패");
            });
            assertThatThrownBy(() -> ask(failing, "오늘 광주 동구 날씨 알려줘"))
                    .isInstanceOf(RuntimeException.class);

            AtomicReference<Prompt> captured = new AtomicReference<>();
            ask(clientOf(capturing(captured, "안녕하세요")), "안녕");

            assertThat(texts(captured.get().getInstructions()))
                    .doesNotContain("오늘 광주 동구 날씨 알려줘")
                    .containsSubsequence("안녕 넌 누구니?", "저는 오마고치입니다", "안녕");
        }
    }

    @Nested
    @DisplayName("정상 완료된 턴")
    class CompletedTurn {

        @Test
        @DisplayName("질문과 답변을 함께 기록한다")
        void savesQuestionAndAnswer() {
            ask(clientOf(answering("서울은 맑습니다")), "서울 날씨 알려줘");

            assertThat(texts(chatMemory.get(CONVERSATION_ID)))
                    .containsExactly("서울 날씨 알려줘", "서울은 맑습니다");
        }

        @Test
        @DisplayName("모델을 바꿔도 앞의 대화가 이어진다")
        void sharesMemoryAcrossModels() {
            ask(clientOf(answering("저는 오마고치입니다")), "안녕 넌 누구니?");

            AtomicReference<Prompt> captured = new AtomicReference<>();
            ask(clientOf(capturing(captured, "네 맞습니다")), "우리가 방금 무슨 이야기 했더라?");

            assertThat(texts(captured.get().getInstructions()))
                    .containsSubsequence("안녕 넌 누구니?", "저는 오마고치입니다");
        }

        @Test
        @DisplayName("답변이 비어 있으면 질문만 남기지 않는다")
        void doesNotKeepQuestionWhenAnswerIsEmpty() {
            ChatClient empty = clientOf(prompt -> new ChatResponse(List.of()));

            ask(empty, "서울 날씨 알려줘");

            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("시스템 메시지는 기록에 남기지 않는다")
        void doesNotStoreSystemMessage() {
            ask(clientOf(answering("안녕하세요")), "안녕");

            assertThat(chatMemory.get(CONVERSATION_ID))
                    .noneMatch(message -> message.getMessageType() == MessageType.SYSTEM);
        }
    }

    @Nested
    @DisplayName("스트리밍")
    class Streaming {

        @Test
        @DisplayName("조각이 여러 개여도 한 번만 기록한다")
        void savesOnceForWholeStream() {
            ChatClient client = streamingClientOf(Flux.just(
                    chunk("서울은 "), chunk("맑습니다")
            ));

            client.prompt()
                    .user("서울 날씨 알려줘")
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                    .stream()
                    .content()
                    .blockLast();

            // 질문 1 + 답변 1. 조각마다 저장됐다면 더 많아진다.
            // 답변은 조각을 합친 전체여야 한다 — 마지막 조각만 남으면 맥락이 잘린다
            assertThat(texts(chatMemory.get(CONVERSATION_ID)))
                    .containsExactly("서울 날씨 알려줘", "서울은 맑습니다");
        }

        @Test
        @DisplayName("빈 스트림으로 끝나면 기록하지 않는다")
        void doesNotSaveWhenStreamIsEmpty() {
            ChatClient client = streamingClientOf(Flux.empty());

            client.prompt()
                    .user("서울 날씨 알려줘")
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                    .stream()
                    .content()
                    .blockLast();

            // 집계기의 context가 비어 대화 ID를 못 찾는 경로다. 저장 전에 빠져나가야 예외도 안 난다
            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("답변 내용이 비어 있으면 기록하지 않는다")
        void doesNotSaveWhenAnswerTextIsBlank() {
            // 스트리밍 집계기는 내용이 없어도 빈 AssistantMessage를 만든다.
            // 개수만 보면 답변이 있는 것으로 오인해 질문만 남는다
            ChatClient client = streamingClientOf(Flux.just(chunk("   ")));

            client.prompt()
                    .user("서울 날씨 알려줘")
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                    .stream()
                    .content()
                    .blockLast();

            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("사용자가 도중에 끊으면 그 턴은 기록하지 않는다")
        void doesNotSaveWhenStreamIsCancelled() {
            // 채팅창을 닫으면 프론트가 요청을 취소한다. 이때도 완결되지 않은 턴이다
            ChatClient client = streamingClientOf(Flux.just(chunk("서울은 "), chunk("맑습니다")));

            client.prompt()
                    .user("서울 날씨 알려줘")
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                    .stream()
                    .content()
                    .next()          // 첫 조각만 받고 구독을 끊는다
                    .block();

            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
        }

        @Test
        @DisplayName("스트림이 끊긴 질문은 다음 턴의 프롬프트에 실리지 않는다")
        void failedStreamDoesNotReachNextTurn() {
            ask(clientOf(answering("저는 오마고치입니다")), "안녕 넌 누구니?");

            ChatClient failing = streamingClientOf(Flux.concat(
                    Flux.just(chunk("오늘 광주는 ")),
                    Flux.error(new IllegalStateException("스트림 중단"))
            ));
            assertThatThrownBy(() -> failing.prompt()
                    .user("오늘 광주 동구 날씨 알려줘")
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                    .stream()
                    .content()
                    .blockLast())
                    .isInstanceOf(RuntimeException.class);

            AtomicReference<Prompt> captured = new AtomicReference<>();
            ask(clientOf(capturing(captured, "안녕하세요")), "안녕");

            assertThat(texts(captured.get().getInstructions()))
                    .doesNotContain("오늘 광주 동구 날씨 알려줘");
        }

        @Test
        @DisplayName("조각을 내보내다 끊기면 그 턴은 기록하지 않는다")
        void doesNotSaveWhenStreamFails() {
            ChatClient client = streamingClientOf(Flux.concat(
                    Flux.just(chunk("서울은 ")),
                    Flux.error(new IllegalStateException("스트림 중단"))
            ));

            List<String> emitted = new ArrayList<>();

            assertThatThrownBy(() -> client.prompt()
                    .user("서울 날씨 알려줘")
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                    .stream()
                    .content()
                    .doOnNext(emitted::add)
                    .blockLast())
                    .isInstanceOf(RuntimeException.class);

            // 사용자에게는 앞 조각이 이미 나갔지만, 완결되지 않은 턴이라 기록하지 않는다
            assertThat(emitted).containsExactly("서울은 ");
            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
        }
    }

    private ChatClient clientOf(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("테스트 시스템 프롬프트")
                .defaultAdvisors(new CompletedTurnChatMemoryAdvisor(chatMemory))
                .build();
    }

    /**
     * 스트리밍은 call()과 경로가 달라 따로 세움
     */
    private ChatClient streamingClientOf(Flux<ChatResponse> responses) {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("사용하지 않음"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return responses;
            }
        };

        return ChatClient.builder(chatModel)
                .defaultSystem("테스트 시스템 프롬프트")
                .defaultAdvisors(new CompletedTurnChatMemoryAdvisor(chatMemory))
                .build();
    }

    private String ask(ChatClient client, String question) {
        return client.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                .call()
                .content();
    }

    /**
     * ChatModel은 call(Prompt) 하나만 추상 메서드라 람다로 스텁 가능함
     */
    private static ChatModel answering(String answer) {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    /**
     * 모델이 실제로 받은 프롬프트를 붙잡음. 기록이 어떻게 전달됐는지 확인하는 유일한 방법
     */
    private static ChatModel capturing(AtomicReference<Prompt> captured, String answer) {
        return prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        };
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .map(Message::getText)
                .toList();
    }
}

package site.omagotchi.learningservice.chat.infrastructure;

import lombok.Getter;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 정상적으로 끝난 대화만 기록에 남기는 ChatMemory Advisor
 * <p>
 * Spring AI의 MessageChatMemoryAdvisor는 모델을 부르기 전에 질문을 저장한다
 * 호출이 실패하면 답 없는 질문만 기록에 남고, 다음 턴에서 모델이 그걸 아직 답하지 않은 질문으로 간주하고 마저 답해버린다
 * 올라마에서 실패한 질문을 제미나이가 떠안는 현상이 일어나는 것이다
 * <p>
 * 여기서는 질문을 프롬프트에만 싣고 저장하지 않는다. 응답이 끝났을 때 after()에서 질문과 답변을 함께 저장한다.
 * 실패하면 after()가 불리지 않아 이번 턴은 통째로 남지 않고, 이전까지의 정상 기록은 그대로 유지된다.
 * <p>
 * MessageChatMemoryAdvisor가 final이라 상속할 수 없어서 직접 구현하였다.
 * 프롬프트 구성(기록을 앞에 붙이고 SystemMessage를 맨 앞으로 올리는 것)은 그쪽 동작을 그대로 따른다
 */
public class CompletedTurnChatMemoryAdvisor implements BaseChatMemoryAdvisor {

    /**
     * 이번 턴의 질문을 담아 두는 곳
     * <p>
     * Advisor는 싱글톤이라 필드에 두면 동시 요청끼리 섞인다.
     * 요청 하나를 따라 흐르는 context를 쓰면 요청별로 분리된다.
     */
    private static final String PENDING_USER_MESSAGE = "omagotchi.chat.memory.pendingUserMessage";

    private final ChatMemory chatMemory;

    @Getter
    private final Scheduler scheduler;

    @Getter
    private final int order;

    public CompletedTurnChatMemoryAdvisor(ChatMemory chatMemory) {
        this(chatMemory, BaseAdvisor.DEFAULT_SCHEDULER, Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
    }

    public CompletedTurnChatMemoryAdvisor(ChatMemory chatMemory, Scheduler scheduler, int order) {
        this.chatMemory = Objects.requireNonNull(chatMemory, "chatMemory");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {

        // 기록 꺼내기
        List<Message> history = this.chatMemory.get(this.getConversationId(request.context()));

        // 지금까지의 대화를 앞에 붙여 모델이 맥락을 보게 한다
        List<Message> messages = new ArrayList<>(history);

        // 기록 + 이번 질문
        messages.addAll(request.prompt().getInstructions());

        // 시스템 메시지를 맨 앞으로
        moveSystemMessageToFront(messages);

        ChatClientRequest augmented = request.mutate()
                .prompt(request.prompt().mutate().messages(messages).build())
                .build();

        // 이번 질문은 아직 저장 X. 성공했을 때 after()가 답변과 함께 저장함
        // context는 요청 하나를 따라다니는 거라서, 여기 넣으면 after()에서 꺼낼 수 있음
        // 그냥 변수에 저장하면 안 되는 이유: 이 Advisor 객체는 앱 전체에 하나만 있음. 여러 사람이 동시에 질문하면 A의 질문이 B의 것으로 덮어씌워짐. context는 요청마다 따로라서 안전함
        return augmented.mutate()
                .context(PENDING_USER_MESSAGE, augmented.prompt().getLastUserOrToolResponseMessage())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        List<Message> answers = answerMessages(response);
        Message question = pendingUserMessage(response.context());

        // 사용자가 실제로 받은 답이 없으면 질문만 남게 되므로 저장 X
        // 개수가 아니라 내용을 봐야 함 - 스트리밍 집계기는 내용이 없어도 빈 AssistantMessage를 만듬
        // 질문이 없을 때 답변만 남기지도 않음. 대화 ID 조회 전에 빠져나가야 빈 스트림(집계 context가 비어 대화 ID를 못 찾는 경우)에서 예외가 나지 않음
        if (answers.isEmpty() || Objects.isNull(question)) {
            return response; // 저장 안 하고 그냥 나감
        }

        List<Message> turn = new ArrayList<>();
        turn.add(question);
        turn.addAll(answers);

        // 질문 + 답변 한 번에
        this.chatMemory.add(this.getConversationId(response.context()), turn);

        return response;
    }

    /**
     * 스트리밍은 조각마다 응답이 오므로 전부 모은 뒤 한 번만 저장한다.
     * <p>
     * BaseAdvisor의 기본 구현은 finishReason이 있는 조각에서 after()를 부르는데, 그 조각의 chatResponse에는 마지막 조각만 들어 있어 답변이 잘린 채 저장된다.
     * 집계기를 물리면 조각을 합친 전체를 한 번만 넘겨준다.
     * <p>
     * 오류로 끝나면 집계가 완료되지 않아 after() 자체가 불리지 않는다.
     * 다만 조각이 하나도 없거나 내용이 비어 있으면 집계기가 빈 응답을 만들어 넘기므로,
     * after()에서 저장할 답이 있는지 다시 확인한다.
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Flux<ChatClientResponse> responses = Mono.just(request)
                .publishOn(this.getScheduler())
                .map(beforeRequest -> this.before(beforeRequest, chain))
                .flatMapMany(chain::nextStream);

        return new ChatClientMessageAggregator()
                .aggregateChatClientResponse(responses, aggregated -> this.after(aggregated, chain));
    }

    /**
     * SystemMessage는 맨 앞에 있어야 한다. 기록을 앞에 붙이면 가운데로 밀리므로 되돌린다.
     */
    private static void moveSystemMessageToFront(List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage) {
                messages.addFirst(messages.remove(i));
                return;
            }
        }
    }

    /**
     * 저장할 값이 있는 답변만 고른다.
     * <p>
     * 내용이 빈 메시지는 사용자가 답을 못 받은 것과 같으므로 답변으로 세지 않는다.
     * (스트리밍 집계기는 조각이 없거나 비어 있어도 빈 AssistantMessage를 만들어 내보낸다)
     */
    private static List<Message> answerMessages(ChatClientResponse response) {
        if (Objects.isNull(response.chatResponse())) {
            return List.of();
        }
        return response.chatResponse().getResults().stream()
                .map(generation -> (Message) generation.getOutput())
                .filter(message -> StringUtils.hasText(message.getText()))
                .toList();
    }

    private static Message pendingUserMessage(Map<String, Object> context) {
        return context.get(PENDING_USER_MESSAGE) instanceof Message message
                ? message
                : null;
    }
}

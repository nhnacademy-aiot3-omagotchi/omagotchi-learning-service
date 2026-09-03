package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventReceiptRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GamificationEventProcessor {

    private final GamificationEventReceiptRepository eventReceiptRepository;
    private final DailyQuestService dailyQuestService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(GamificationEventMessage event) {
        boolean claimed = eventReceiptRepository.claim(
                event.eventType(),
                event.sourceId(),
                event.userId(),
                event.occurredAt()
        );
        if (!claimed) {
            return;
        }

        switch (event.eventType()) {
            case ATTENDANCE_CHECKED_IN -> dailyQuestService.handleAttendance(event.userId());
            case STUDY_COMPLETED -> completeStudyQuests(event.userId());
        }
    }

    /**
     * 학습 종료 한 번으로 루틴 퀘스트와 AI 추천 퀘스트의 완료 여부를 함께 판정한다.
     *
     * <p>AI 추천 퀘스트는 예측 기반 학습 시간 퀘스트이므로 학습 종료 시점의 누적 공부시간을
     * 다시 읽어 목표 도달 여부를 판정한다. 목표 미달이면 진행 중 상태를 유지한다.
     *
     * <p>둘을 한 곳에서 부르는 이유는 하나의 학습 이벤트에서 관련 퀘스트 처리가 빠지지 않게
     * 하기 위해서다. 루틴 퀘스트는 학습 1회로 완료하고, 시간형 퀘스트는 누적시간으로 판정한다.
     *
     * <p>이미 완료된 퀘스트에 다시 호출해도 상태는 그대로 유지된다. 보상 중복은 수령 단계의
     * XpTransaction 원장이 막는다.
     */
    private void completeStudyQuests(UUID userId) {
        dailyQuestService.handleStudyCompleted(userId);
        dailyQuestService.handleLlmQuestCompleted(userId);
    }
}

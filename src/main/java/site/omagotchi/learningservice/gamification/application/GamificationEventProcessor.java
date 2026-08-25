package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventReceiptRepository;

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
            case STUDY_COMPLETED -> dailyQuestService.handleStudyCompleted(event.userId());
        }
    }
}

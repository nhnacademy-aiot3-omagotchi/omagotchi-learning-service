package site.omagotchi.learningservice.gamification.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.gamification.application.GamificationEventProcessor;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;

@Component
@RequiredArgsConstructor
public class GamificationDomainEventListener {

    private final GamificationEventProcessor eventProcessor;

    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttendanceCheckedIn(AttendanceCheckedInEvent event) {
        eventProcessor.process(event);
    }

    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudyCompleted(StudyCompletedEvent event) {
        eventProcessor.process(event);
    }
}

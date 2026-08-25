package site.omagotchi.learningservice.gamification.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.gamification.application.GamificationEventOutboxService;
import site.omagotchi.learningservice.gamification.application.GamificationEventRetryCoordinator;
import site.omagotchi.learningservice.gamification.application.GamificationEventType;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;

@Component
@RequiredArgsConstructor
public class GamificationDomainEventListener {

    private final GamificationEventOutboxService outboxService;
    private final GamificationEventRetryCoordinator retryCoordinator;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void persistAttendanceCheckedIn(AttendanceCheckedInEvent event) {
        outboxService.enqueue(event);
    }

    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttendanceCheckedIn(AttendanceCheckedInEvent event) {
        retryCoordinator.dispatch(new GamificationEventOutboxRepository.EventKey(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                event.attendanceId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void persistStudyCompleted(StudyCompletedEvent event) {
        outboxService.enqueue(event);
    }

    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudyCompleted(StudyCompletedEvent event) {
        retryCoordinator.dispatch(new GamificationEventOutboxRepository.EventKey(
                GamificationEventType.STUDY_COMPLETED,
                event.sourceId().toString()));
    }
}

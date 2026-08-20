package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;

@Service
@RequiredArgsConstructor
public class GamificationEventOutboxService {

    private final GamificationEventOutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(AttendanceCheckedInEvent event) {
        outboxRepository.enqueue(new GamificationEventMessage(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                event.attendanceId().toString(),
                event.userId(),
                event.occurredAt()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(StudyCompletedEvent event) {
        outboxRepository.enqueue(new GamificationEventMessage(
                GamificationEventType.STUDY_COMPLETED,
                event.sourceId().toString(),
                event.userId(),
                event.occurredAt()));
    }
}

package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventReceiptRepository;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;

@Service
@RequiredArgsConstructor
public class GamificationEventProcessor {

    private final GamificationEventReceiptRepository eventReceiptRepository;
    private final DailyQuestService dailyQuestService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(AttendanceCheckedInEvent event) {
        boolean claimed = eventReceiptRepository.claim(
                GamificationEventType.ATTENDANCE_CHECKED_IN,
                event.attendanceId().toString(),
                event.userId(),
                event.occurredAt()
        );
        if (claimed) {
            dailyQuestService.handleAttendance(event.userId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(StudyCompletedEvent event) {
        boolean claimed = eventReceiptRepository.claim(
                GamificationEventType.STUDY_COMPLETED,
                event.sourceId().toString(),
                event.userId(),
                event.occurredAt()
        );
        if (claimed) {
            dailyQuestService.handleStudyCompleted(event.userId());
        }
    }
}

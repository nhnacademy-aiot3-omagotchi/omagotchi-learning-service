package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.attendance.application.port.AttendanceEventPublisher;

@Component
@RequiredArgsConstructor
public class SpringAttendanceEventPublisher implements AttendanceEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishCheckedIn(AttendanceCheckedInEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

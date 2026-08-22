package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;

/**
 * 출결 Application 계층이 출석 사실을 외부로 알리는 발행 경계다.
 */
public interface AttendanceEventPublisher {

    void publishCheckedIn(AttendanceCheckedInEvent event);
}

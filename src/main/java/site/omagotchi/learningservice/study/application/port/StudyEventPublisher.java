package site.omagotchi.learningservice.study.application.port;

import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;

/**
 * 학습 Application 계층이 완료 사실을 외부로 알리는 발행 경계다.
 */
public interface StudyEventPublisher {

    void publishCompleted(StudyCompletedEvent event);
}

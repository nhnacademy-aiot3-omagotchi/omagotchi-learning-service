package site.omagotchi.learningservice.study.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;
import site.omagotchi.learningservice.study.application.port.StudyEventPublisher;

@Component
@RequiredArgsConstructor
public class SpringStudyEventPublisher implements StudyEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishCompleted(StudyCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

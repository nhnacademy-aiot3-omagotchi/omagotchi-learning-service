package site.omagotchi.learningservice.rule.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.rule.application.event.ThresholdRuleChangedEvent;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleEventPublisher;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

@RequiredArgsConstructor
@Component
public class SpringThresholdRuleEventPublisher implements ThresholdRuleEventPublisher {

    private final ApplicationEventPublisher publisher;

    /**
     * publishEvent()는 리스너 실행을 예약할 뿐이다. 브로커로 나가는 것은 커밋 이후,
     * ThresholdRuleChangedPublisher에서다.
     */
    @Override
    public void publishThresholdRuleChanged(ThresholdRule rule) {
        publisher.publishEvent(ThresholdRuleChangedEvent.from(rule));
    }
}
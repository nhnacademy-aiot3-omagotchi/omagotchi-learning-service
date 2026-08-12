package site.omagotchi.learningservice.cohort.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;

/**
 * {@link CohortEventPublisher}를 Spring 이벤트로 구현한다.
 *
 * <p>얇은 위임인데도 Class를 두는 이유는 {@code ApplicationEventPublisher}가 Framework
 * Type이기 때문이다. Application이 이것을 직접 주입받으면 발행 수단을 바꿀 때
 * (예: 아웃박스나 큐) 서비스 Code가 함께 바뀐다
 * ({@code SpringOccupancyEventPublisher}와 같은 판단).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringCohortEventPublisher implements CohortEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishMembershipEnded(CohortMembershipEndedEvent event) {
        log.debug("멤버십 종료 이벤트 발행. membershipId={}, cohortId={}",
                event.membershipId(), event.cohortId());
        applicationEventPublisher.publishEvent(event);
    }
}

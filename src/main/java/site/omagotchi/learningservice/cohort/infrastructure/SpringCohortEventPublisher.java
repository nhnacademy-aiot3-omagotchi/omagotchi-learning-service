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
 *
 * <p><b>아직 이 이벤트를 받는 리스너가 없다.</b> 팀 정리(GR-16)와 점유 정리(MR-26)가
 * 소비처이며, 지금은 발행 지점만 고정해 둔 상태다.</p>
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

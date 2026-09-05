package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.cohort.application.event.CohortClosedEvent;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.occupancy.application.CohortEndedCleanup;

/**
 * 기수 종료 정리 훅의 진입점 (CE-05, 명세 08 §7 "트리거 방식").
 *
 * <p>이벤트·단일 리스너·트리거 선택의 근거는 ADR space-team/0015. {@code AFTER_COMMIT} +
 * {@code @Async}는 {@code CohortMembershipEndedEvent} 계열과 같은 이유다 (ADR 0006).</p>
 *
 * <p><b>이 이벤트가 유실되면 CE-04(공간 관리 주체 해제)만은 복구되지 않는다.</b> 팀·점유는
 * 멤버십 스윕(ADR 0013)이 뒤늦게 받치지만, CE-04는 기수 단위라 받침이 없고 재트리거·수동
 * 복구 수단도 없다 — 그 기수가 관리하던 공간이 {@code cohort_id}를 계속 가리킨 채 동결된다.
 * 상시 스윕을 두지 않기로 한 이유는 ADR 0015 §5.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CohortClosedListener {

    private final CohortEndedCleanup cohortEndedCleanup;

    /**
     * 이벤트에 기록된 종료 시각으로 정해진 여섯 단계를 밟는다.
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCohortClosed(CohortClosedEvent event) {
        log.info("기수 종료 정리를 시작합니다. cohortId={}", event.cohortId());
        cohortEndedCleanup.cleanUp(event.cohortId(), event.closedAt());
    }
}

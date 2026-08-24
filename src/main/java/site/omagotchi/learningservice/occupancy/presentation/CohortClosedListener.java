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
 * <p><b>이벤트를 택하고 배치를 택하지 않았다.</b> 명세가 남긴 "종료 판정 기준"에 대한
 * 답이기도 하다 — {@code cohorts.end_date}는 예정일이라 연장·조기 종료로 어긋나는 반면,
 * {@code ACTIVE → CLOSED} 전이는 관리자가 실제로 끝냈다는 유일한 사실이다. 폴링이면
 * 종료와 실습실 해제 사이에 주기만큼 공백이 생겨 다음 기수 배정이 그동안 409로 막힌다.</p>
 *
 * <p><b>리스너가 팀·알림·점유·실습실로 쪼개지지 않는 것이 {@code CohortMembershipEndedEvent}
 * 계열과의 차이다.</b> 저쪽은 팀과 점유가 서로의 결과를 보지 않아 나눌수록 격리가 좋아지지만,
 * 여기는 CE-05가 네 단계의 순서를 강제한다 — 나누면 그 순서를 잃는다. 단계별 격리는
 * {@link CohortEndedCleanup} 안에서 단계마다 Transaction을 나누는 방식으로 지킨다.</p>
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b> (ADR space-team/0006): 커밋 전에 받으면 롤백될 수도
 * 있는 종료를 근거로 팀을 해체하고 점유를 끝내게 된다. 되돌릴 수 없는 물리 삭제가
 * 섞여 있어 특히 그렇다.</p>
 *
 * <p><b>{@code @Async}인 이유</b>: 정리 실패가 기수 종료를 롤백시키면 안 된다 — 명세 08 §5가
 * "기수 종료는 롤백하지 않음"으로 못박았다. 관리자에게는 종료가 성공으로 응답되고,
 * 실패한 단계는 기록만 남는다.</p>
 *
 * <p><b>이 이벤트가 유실되면 복구 경로는 부분적이다.</b> 기수 종료가 소속을 전부 ENDED로
 * 전이하므로 팀·점유는 기존 멤버십 스윕(ADR space-team/0013)이 뒤늦게라도 받치지만,
 * 실습실 해제(CE-04)는 기수 단위라 받침이 없다 — 다음 기수 배정이 409로 막히는 형태로
 * 드러나고, 종료를 다시 트리거하면 멱등하게 복구된다 (명세 08 §7 미해결).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CohortClosedListener {

    private final CohortEndedCleanup cohortEndedCleanup;

    /**
     * 정해진 순서로 네 단계를 밟는다.
     *
     * <p>같은 이벤트가 두 번 도착해도 안전하다 — 각 단계가 조건부 연산이라 두 번째는
     * 대상이 없다 (명세 08 §5 "훅 중복 수신").</p>
     *
     * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 하나로 두르면 마지막 단계의 실패가
     * 팀 해체까지 되돌려 단계별 격리가 사라진다. Transaction 경계는 각 단계가 소유한다.</p>
     *
     * <p>예외를 여기서 잡지 않는다 — 단계별 처리는 이미 {@link CohortEndedCleanup} 안에서
     * 격리되므로, 여기까지 올라온 예외는 훅 자체가 깨진 경우다. {@code @Async}의 예외
     * 처리기가 기록하게 둔다.</p>
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCohortClosed(CohortClosedEvent event) {
        log.info("기수 종료 정리를 시작합니다. cohortId={}", event.cohortId());
        cohortEndedCleanup.cleanUp(event.cohortId());
    }
}

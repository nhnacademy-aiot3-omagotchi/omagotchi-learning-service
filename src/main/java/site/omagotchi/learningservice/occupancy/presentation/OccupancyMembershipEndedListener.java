package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipPresenceCleanup;

/**
 * 소속이 끝난 사람의 점유·참여와 출결·체류를 순서대로 정리한다 (MR-26, CE-07).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 이벤트도 외부
 * 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고 "무엇을
 * 할지"는 Application이 소유한다 ({@code TeamMembershipEndedListener}와 같은 배치).</p>
 *
 * <p><b>팀 정리와 별개의 리스너인 것이 의도다.</b> 명세 06 §5가 "팀 처리 성공, 점유 처리
 * 실패"에 단계별 격리를 요구하는데, 리스너를 나누면 한쪽의 실패가 다른 쪽을 되돌리지
 * 않는다. 반면 출결은 회의가 먼저 닫혀야 하므로 점유와 같은 리스너 아래에서 순서를
 * 고정한다.</p>
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b> (ADR space-team/0006): 커밋 전에 받으면 롤백될
 * 수도 있는 소속 종료를 근거로 남의 점유를 끝내게 된다.</p>
 *
 * <p><b>{@code @Async}인 이유</b>: 후속 정리 실패가 소속 종료를 롤백시키면 안 된다.
 * 계정이 삭제됐는데 정리에 실패했다고 삭제를 되돌리면 인증 파트와 상태가 어긋난다.</p>
 *
 * <p>출결 재처리 경로는 PR 3의 정합성 스윕이 담당한다. 이 리스너는 정상 이벤트 경로만
 * 소유한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OccupancyMembershipEndedListener {

    private final EndedMembershipPresenceCleanup presenceCleanup;

    /**
     * 점유와 참여를 먼저 정리하고, 이어서 출결과 체류를 마감한다.
     *
     * <p>같은 이벤트가 두 번 도착해도 안전하다 — 이미 종료된 점유와 닫힌 참여·체류,
     * 확정된 미퇴실 출결은 각 정리 서비스가 건너뛴다.</p>
     *
     * <p><b>{@code @Transactional}을 붙이지 않는다.</b> {@code AFTER_COMMIT} + {@code @Async}라
     * 이 Method는 원 Transaction이 끝난 뒤 다른 Thread에서 실행되고, 그 상태에서
     * 조정 서비스가 점유와 출결의 독립 Transaction을 차례로 연다 — ADR이 말한
     * "커밋된 데이터 기준 재조회"가 그대로 성립한다.</p>
     *
     * <p>단계별 예외는 조정 서비스가 기록하고 격리한다. 점유 실패가 일반 재실 출결의
     * 마감 시도 자체를 막지 않게 하기 위함이다.</p>
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipEnded(CohortMembershipEndedEvent event) {
        presenceCleanup.cleanUp(
                event.membershipId(), event.userId(), event.endedAt());
    }
}

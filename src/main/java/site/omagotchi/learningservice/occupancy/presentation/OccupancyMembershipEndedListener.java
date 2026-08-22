package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipOccupancyCleanup;

/**
 * 소속이 끝난 사람의 점유·참여를 정리한다 (MR-26, CE-07).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 이벤트도 외부
 * 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고 "무엇을
 * 할지"는 Application이 소유한다 ({@code TeamMembershipEndedListener}와 같은 배치).</p>
 *
 * <p><b>팀 정리와 별개의 리스너인 것이 의도다.</b> 명세 06 §5가 "팀 처리 성공, 점유 처리
 * 실패"에 단계별 격리를 요구하는데, 리스너를 나누면 한쪽의 실패가 다른 쪽을 되돌리지
 * 않는다. 순서에 의존하지도 않는다 — 팀과 점유는 서로의 결과를 보지 않는다.</p>
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b> (ADR space-team/0006): 커밋 전에 받으면 롤백될
 * 수도 있는 소속 종료를 근거로 남의 점유를 끝내게 된다.</p>
 *
 * <p><b>{@code @Async}인 이유</b>: 점유 정리 실패가 소속 종료를 롤백시키면 안 된다.
 * 계정이 삭제됐는데 점유 정리에 실패했다고 삭제를 되돌리면 인증 파트와 상태가 어긋난다.</p>
 *
 * <p><b>재처리 경로가 아직 없다.</b> 이벤트가 유실되면 점유가 만료 시각까지 잔존한다 —
 * 팀이 같은 이벤트에 대해 {@code EndedMembershipSweep}(ADR space-team/0013)을 둔 것과
 * 같은 이유로 정합성 스윕이 필요하며, 별도 작업으로 남겨 두었다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OccupancyMembershipEndedListener {

    private final EndedMembershipOccupancyCleanup occupancyCleanup;

    /**
     * 점유와 참여를 정리한다.
     *
     * <p>같은 이벤트가 두 번 도착해도 안전하다 — 이미 종료된 점유는 활성 조회에서 빠지고,
     * 닫힌 참여 행은 조건부 UPDATE가 건너뛴다.</p>
     *
     * <p><b>{@code @Transactional}을 붙이지 않는다.</b> {@code AFTER_COMMIT} + {@code @Async}라
     * 이 Method는 원 Transaction이 끝난 뒤 다른 Thread에서 실행되고, 그 상태에서
     * {@code cleanUp}의 {@code @Transactional}이 새 Transaction을 연다 — ADR이 말한
     * "커밋된 데이터 기준 재조회"가 그대로 성립한다.</p>
     *
     * <p><b>예외를 삼키지 않는다.</b> {@code @Async}가 잡아 {@code AsyncConfig}의 예외
     * 처리기가 기록한다. 여기서 잡으면 실패가 성공처럼 보이고, 재처리 경로를 붙일 때
     * 실패 신호가 없어진다.</p>
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipEnded(CohortMembershipEndedEvent event) {
        boolean released = occupancyCleanup.cleanUp(
                event.membershipId(), event.userId(), event.endedAt());

        if (released) {
            log.info("소속 종료로 점유를 정리했습니다. membershipId={}", event.membershipId());
        }
    }
}

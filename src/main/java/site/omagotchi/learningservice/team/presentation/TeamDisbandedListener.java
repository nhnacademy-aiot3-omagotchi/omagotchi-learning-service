package site.omagotchi.learningservice.team.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.team.application.TeamDisbandNotifier;
import site.omagotchi.learningservice.team.application.event.TeamDisbandedEvent;

/**
 * 팀이 해체됐음을 (구)팀원에게 통보한다 (GR-19).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 이벤트도 외부
 * 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고 "무엇을
 * 할지"는 Application이 소유한다.</p>
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b>: 해체가 롤백되면 팀원 행도 함께 되살아난다 —
 * 커밋 전에 통보하면 <b>멀쩡히 남아 있는 팀을 해체됐다고 알리게 된다.</b></p>
 *
 * <p><b>{@code @Async}인 이유</b> (명세 06 §5): 통보 실패가 해체를 롤백시키면 안 된다.
 * 알림 파트 장애로 마스터가 팀을 삭제하지 못하면 안 되기 때문이다.</p>
 *
 * <p><b>재처리 경로를 두지 않는다.</b> 통보 대상인 {@code team_members} 행을 이미 지운
 * 뒤라 재시도할 원천이 남지 않는다 — at-most-once가 이 통보의 계약이다. 정합성 스윕으로도
 * 복구할 수 없다: "이 사람들에게 통보를 빚졌다"는 흔적이 DB에 없다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamDisbandedListener {

    private final TeamDisbandNotifier disbandNotifier;

    /**
     * (구)팀원에게 통보한다.
     *
     * <p>수신자를 이벤트에서 꺼내 쓰는 것이 이 리스너의 특징이다 — 팀원 행이 물리 삭제라
     * 여기서 조회할 수 없다 ({@code TeamDisbandedEvent} 참고).</p>
     *
     * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 읽기만 하고 쓰지 않는다 — 발송
     * 기록 같은 후속 상태가 없으므로 Transaction을 열 이유가 없다.</p>
     *
     * <p><b>예외를 삼키지 않는다.</b> 건별 실패는 Notifier가 이미 잡아 기록하므로, 여기까지
     * 올라온 것은 통보 절차 자체가 실패했다는 뜻이다 — {@code @Async}가 받아
     * {@code AsyncConfig}의 예외 처리기가 남긴다.</p>
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamDisbanded(TeamDisbandedEvent event) {
        disbandNotifier.notifyDisbanded(
                event.teamId(), event.teamName(), event.cohortMembershipIds(), event.disbandedAt());
    }
}

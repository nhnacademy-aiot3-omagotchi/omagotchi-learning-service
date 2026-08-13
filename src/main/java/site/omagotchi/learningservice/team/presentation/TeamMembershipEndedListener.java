package site.omagotchi.learningservice.team.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.team.application.TeamMasterService;

/**
 * 소속이 끝난 사람을 팀에서 정리한다 (GR-16).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 Message와 이벤트도
 * 외부 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고
 * "무엇을 할지"는 Application이 소유한다.</p>
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b> (ADR space-team/0006): 커밋 전에 받으면 롤백될
 * 수도 있는 종료를 근거로 팀을 해체하게 된다. 소속 종료가 확정된 뒤에만 정리한다.</p>
 *
 * <p><b>{@code @Async}인 이유</b>: 팀 정리 실패가 소속 종료를 롤백시키면 안 된다.
 * 계정이 삭제됐는데 팀 정리에 실패했다고 삭제를 되돌리면, 인증 파트와 상태가 어긋난다.</p>
 *
 * <p><b>실행기 이름을 지정하는 이유</b>: {@code AsyncConfig}가 도메인 이벤트 전용 실행기만
 * 정의하고 기본 실행기는 Spring Boot 것을 그대로 두기 때문이다. 이름을 빼면 이 리스너가
 * HTTP 비동기 처리와 같은 풀에서 돌아, 전용 큐 정책과 스레드 이름 규칙이 적용되지 않는다.</p>
 *
 * <p><b>트랜잭션 경계</b>: 여기에 {@code @Transactional}을 붙이지 않는다.
 * {@code AFTER_COMMIT} + {@code @Async}라 이 Method는 원 트랜잭션이 끝난 뒤 다른
 * 스레드에서 실행되고, 그 상태에서 {@code TeamMasterService.removeEndedMember}의
 * {@code @Transactional}이 새 트랜잭션을 연다 — ADR이 경고한 "커밋된 데이터 기준 재조회"가
 * 그대로 성립한다. 여기에 한 겹 더 두르면 경계만 늘고 얻는 것이 없다.</p>
 *
 * <p><b>예외를 삼키지 않는다.</b> {@code @Async}가 잡아 {@code AsyncConfig}의 예외 처리기가
 * 기록한다. 여기서 잡아버리면 실패가 성공처럼 보이고, 나중에 재처리 큐를 붙일 때
 * 실패 신호가 없어진다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamMembershipEndedListener {

    private final TeamMasterService teamMasterService;

    /**
     * 팀 소속을 정리한다.
     *
     * <p>같은 이벤트가 두 번 도착해도 안전하다 — 소속 행이 이미 없으면
     * {@code removeEndedMember}가 아무것도 하지 않는다.</p>
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipEnded(CohortMembershipEndedEvent event) {
        boolean removed = teamMasterService.removeEndedMember(event.membershipId());
        if (removed) {
            log.info("소속 종료로 팀에서 정리했습니다. membershipId={}", event.membershipId());
        }
    }
}

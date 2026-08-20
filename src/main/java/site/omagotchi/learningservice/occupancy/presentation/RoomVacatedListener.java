package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.global.config.AsyncConfig;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertDispatcher;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;

/**
 * 회의실이 비면 공실 알림 대기자에게 발송한다 (MR-03, MR-18).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 이벤트도 외부
 * 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고 "무엇을
 * 할지"는 Application이 소유한다 ({@code TeamMembershipEndedListener}와 같은 배치).</p>
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b> (ADR space-team/0006): 커밋 전에 받으면 롤백될
 * 수도 있는 종료를 근거로 "방이 비었다"고 알리게 된다. 알림은 되돌릴 수 없다.</p>
 *
 * <p><b>{@code @Async}인 이유</b> (MR-18): 발송 실패가 점유 해제를 롤백시키면 안 된다.
 * 반납은 성공했는데 텔레그램 장애로 되돌아가면, 사용자는 방을 반납할 수 없게 된다.</p>
 *
 * <p><b>실행기 이름을 지정하는 이유</b>: {@code AsyncConfig}가 도메인 이벤트 전용 실행기만
 * 정의하고 기본 실행기는 Spring Boot 것을 그대로 두기 때문이다 (ADR space-team/0012).
 * 이름을 빼면 이 리스너가 HTTP 비동기 처리와 같은 풀에서 돈다.</p>
 *
 * <p><b>강제 종료는 여기 오지 않는다</b> (MR-21). {@code FORCE_RELEASED}는 이벤트를
 * 발행하지 않으며, 그쪽의 대기 신청은 발송이 아니라 물리 삭제 대상이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomVacatedListener {

    private final VacancyAlertDispatcher vacancyAlertDispatcher;

    /**
     * 대기자 전원에게 발송한다.
     *
     * <p>같은 이벤트가 두 번 도착해도 안전하다 — 첫 처리가 {@code notified_at}을 찍어
     * 두 번째에는 대기 중 후보가 남지 않는다.</p>
     *
     * <p><b>{@code @Transactional}을 붙이지 않는다.</b> {@code AFTER_COMMIT} + {@code @Async}라
     * 이 Method는 원 Transaction이 끝난 뒤 다른 Thread에서 실행되고, 발송 건별 Transaction은
     * {@code VacancyAlertDelivery}가 {@code REQUIRES_NEW}로 연다. 여기에 한 겹 더 두르면
     * 경계만 늘고 잠금 구간이 길어진다.</p>
     *
     * <p><b>예외를 삼키지 않는다.</b> 건별 실패는 Dispatcher가 이미 잡아 기록하므로,
     * 여기까지 올라온 것은 발송 절차 자체가 실패했다는 뜻이다 — {@code @Async}가 받아
     * {@code AsyncConfig}의 예외 처리기가 남긴다.</p>
     */
    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomVacated(RoomVacatedEvent event) {
        vacancyAlertDispatcher.dispatch(event.spaceId(), event.vacatedAt());
    }
}

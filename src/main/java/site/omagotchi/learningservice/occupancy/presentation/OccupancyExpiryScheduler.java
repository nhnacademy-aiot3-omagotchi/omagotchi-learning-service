package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;

/**
 * 만료된 점유를 주기적으로 정리하는 진입점 (#9).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 Scheduler와 Batch도
 * 외부 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고
 * "무엇을 할지"는 Application이 소유한다.</p>
 *
 * <p><b>왜 필요한가.</b> 점유 시작이 {@code expireStale*}로 정리를 일부 대행하지만 그것은
 * 누군가 그 공간을 점유하려 하거나 그 계정이 새로 점유할 때만 돈다. 아무도 찾지 않는 방은
 * 만료돼도 ACTIVE로 남아 목록에 "사용 중"으로 뜨고, 그 참여자들은 열린 채 남아
 * {@code uq_occupancy_participants_one_active} 때문에 다른 회의에 들어가지 못한다.</p>
 *
 * <p><b>실패는 다음 주기가 처리한다.</b> 여기서 예외를 잡아 삼키는 것은 스케줄러 계약
 * 때문이다 — {@code @Scheduled} Method가 예외를 던지면 Spring이 로그만 남기지만,
 * {@code fixedDelay}는 다음 실행을 계속 잡아주므로 결과적으로 자연 재시도가 된다.
 * 다만 그 로그는 Spring 내부 Logger로 나가 원인 추적이 어려워, 이 경계에서 한 번
 * 명시적으로 기록한다 (04-error-handling §5, 비 HTTP 실패).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OccupancyExpiryScheduler {

    private final RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    /**
     * 만료 정리 주기 실행.
     *
     * <p>{@code fixedRate}가 아니라 {@code fixedDelay}인 것이 의도다. 정리가 주기보다 오래
     * 걸리면 {@code fixedRate}는 실행을 겹치게 만들어 같은 점유에 이벤트를 두 번 발행한다.
     * {@code fixedDelay}는 이전 실행이 끝난 뒤부터 세므로 한 인스턴스 안에서는 겹치지 않는다.</p>
     *
     * <p>주기가 곧 <b>알림 지연의 상한</b>이다. 만료 직후 비워진 방을 대기자가 알기까지
     * 최대 이 시간이 걸린다. 짧게 잡으면 알림은 빨라지지만 빈 조회가 늘어난다.</p>
     */
    @Scheduled(
            fixedDelayString = "${omagotchi.occupancy.expiry.fixed-delay:60000}",
            initialDelayString = "${omagotchi.occupancy.expiry.initial-delay:30000}"
    )
    public void expireStaleOccupancies() {
        try {
            roomOccupancyLifecycleService.sendExpiryReminders();
        } catch (Exception exception) {
            // 알림 실패가 실제 만료 정리를 막아서는 안 된다.
            log.error("점유 만료 임박 알림 처리에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }

        try {
            roomOccupancyLifecycleService.expireAll();
        } catch (Exception exception) {
            // 삼키고 다음 주기에 맡긴다. 여기서 다시 던지면 이 실행만 실패로 남고
            // 정리는 어차피 다음 주기가 하므로, 원인을 남기는 것이 유일하게 의미 있는 처리다.
            log.error("만료된 점유 정리에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}

package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipOccupancySweep;

/**
 * 소속이 끝난 사람의 점유·참여 정리를 주기적으로 확인하는 진입점 (MR-26, ADR space-team/0013).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 Scheduler도 외부
 * 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고 "무엇을
 * 할지"는 Application이 소유한다 ({@code OccupancyExpiryScheduler}와 같은 구조).</p>
 *
 * <p><b>왜 필요한가.</b> {@code OccupancyMembershipEndedListener}가 커밋 직후 정리하지만,
 * 그것이 실패하면 복구 경로가 없다 — 인메모리 이벤트는 전달 상태를 보존하지 않고
 * {@code CohortMembershipService#end}는 이미 ENDED인 소속에 이벤트를 다시 내지 않는다.
 * 그 사이 방은 "사용 중"으로 잠기고, 열린 참여 행은
 * {@code uq_occupancy_participants_one_active}가 계정 기준이라 그 사람이 다시는 어떤
 * 회의에도 들어가지 못하게 만든다.</p>
 *
 * <p><b>만료 스케줄러가 있는데도 필요한 이유</b>는 지연이다. 만료 정리는 {@code expires_at}
 * 경과를 기다리므로 최대 3시간(2h + 연장 1h)이 걸리고, 그동안 참여자들이 묶여 있다.</p>
 *
 * <p><b>실패는 다음 주기가 처리한다.</b> {@code @Scheduled} Method가 예외를 던지면 Spring
 * 내부 Logger로 나가 추적이 어려우므로, 이 경계에서 한 번 명시적으로 기록한다
 * (04-error-handling §5, 비 HTTP 실패).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndedMembershipOccupancySweepScheduler {

    private final EndedMembershipOccupancySweep occupancySweep;

    /**
     * 한 배치에서 훑을 참여 행 수.
     *
     * <p>열린 참여는 계정당 최대 1건이라(부분 유니크) 전체 행 수가 현재 이용자 수로
     * 유계지만, 한 번에 전건을 들고 오지 않도록 배치로 나눈다 (ADR 0013 §6).</p>
     */
    @Value("${omagotchi.occupancy.membership-sweep.batch-size:200}")
    private int batchSize;

    /**
     * 정합성 스윕 주기 실행.
     *
     * <p>{@code fixedRate}가 아니라 {@code fixedDelay}인 것은 정리가 주기보다 오래 걸릴 때
     * 실행이 겹치지 않게 하기 위해서다 ({@code OccupancyExpiryScheduler}와 같은 판단).</p>
     *
     * <p>주기가 곧 <b>복구 지연의 상한</b>이다. 이벤트가 실패한 뒤 묶인 사람이 풀리기까지
     * 최대 이 시간이 걸린다. 만료 정리(1분)보다 길게 잡는 것은 이 경로가 "이미 실패한 건"만
     * 다루는 예외 경로이기 때문이다 — 팀 정리 스윕과 같은 주기를 쓴다.</p>
     */
    @Scheduled(
            fixedDelayString = "${omagotchi.occupancy.membership-sweep.fixed-delay:300000}",
            initialDelayString = "${omagotchi.occupancy.membership-sweep.initial-delay:60000}"
    )
    public void sweepEndedMemberships() {
        try {
            occupancySweep.sweep(batchSize);
        } catch (Exception exception) {
            log.error("소속 종료 점유 정합성 스윕에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}

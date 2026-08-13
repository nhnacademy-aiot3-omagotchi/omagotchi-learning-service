package site.omagotchi.learningservice.team.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.team.application.EndedMembershipSweep;

/**
 * 고아 팀원 정리를 주기적으로 확인하는 진입점 (ADR space-team/0013).
 *
 * <p>{@code presentation}에 있는 것이 Convention이다 — HTTP뿐 아니라 Scheduler와 Batch도
 * 외부 진입점이다 (10-backend-code-structure §2). 이 Class는 "언제 부를지"만 알고
 * "무엇을 할지"는 Application이 소유한다 ({@code OccupancyExpiryScheduler}와 같은 구조).</p>
 *
 * <p><b>왜 필요한가.</b> {@code TeamMembershipEndedListener}가 커밋 직후 정리하지만,
 * 그것이 실패하면 복구 경로가 없다 — 인메모리 이벤트는 전달 상태를 보존하지 않고
 * {@code CohortMembershipService#end}는 이미 ENDED인 소속에 이벤트를 다시 내지 않는다.
 * 고아 행이 MASTER면 팀이 영구히 잠긴다.</p>
 *
 * <p><b>실패는 다음 주기가 처리한다.</b> {@code @Scheduled} Method가 예외를 던지면 Spring
 * 내부 Logger로 나가 추적이 어려우므로, 이 경계에서 한 번 명시적으로 기록한다
 * (04-error-handling §5, 비 HTTP 실패).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndedMembershipSweepScheduler {

    private final EndedMembershipSweep endedMembershipSweep;

    /**
     * 한 배치에서 훑을 소속 행 수.
     *
     * <p>{@code team_members}는 물리 삭제라 전체 행 수가 현재 소속 수로 유계지만,
     * 한 번에 전건을 들고 오지 않도록 배치로 나눈다 (ADR 0013 §6).</p>
     */
    @Value("${omagotchi.team.sweep.batch-size:200}")
    private int batchSize;

    /**
     * 정합성 스윕 주기 실행.
     *
     * <p>{@code fixedRate}가 아니라 {@code fixedDelay}인 것은 정리가 주기보다 오래 걸릴 때
     * 실행이 겹치지 않게 하기 위해서다 ({@code OccupancyExpiryScheduler}와 같은 판단).</p>
     *
     * <p>주기가 곧 <b>복구 지연의 상한</b>이다. 이벤트가 실패한 뒤 고아 행이 정리되기까지
     * 최대 이 시간이 걸린다. 만료 정리(1분)보다 길게 잡는 것은 이 경로가 "이미 실패한 건"만
     * 다루는 예외 경로이기 때문이다.</p>
     */
    @Scheduled(
            fixedDelayString = "${omagotchi.team.sweep.fixed-delay:300000}",
            initialDelayString = "${omagotchi.team.sweep.initial-delay:60000}"
    )
    public void sweepEndedMemberships() {
        try {
            endedMembershipSweep.sweep(batchSize);
        } catch (Exception exception) {
            // 삼키고 다음 주기에 맡긴다. 정리는 어차피 다음 주기가 다시 하므로
            // 원인을 남기는 것이 유일하게 의미 있는 처리다.
            log.error("고아 팀원 정합성 스윕에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}

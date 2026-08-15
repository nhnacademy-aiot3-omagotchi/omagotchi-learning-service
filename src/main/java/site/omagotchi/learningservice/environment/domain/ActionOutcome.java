package site.omagotchi.learningservice.environment.domain;

import java.time.Instant;

/***
 * 룰 히트 때 어떤걸 했는지 확인용 영수증. 룰 히트가 아닌 타입은 none
 *
 * @param action 룰 히트 됐을 때 취할 조치
 * @param status 룰 히트 됐을 때 조치 상태
 * @param confirmedAt 조치를 요청했을 때 아두이노에 전달 시간 (status.CONFIRM 일때만)
 * @param simulated 시뮬레이션 성공 여부
 * @param error 시뮬레이션 실패 시 원인
 * @param notifiedAt 텔레그램에 발신한 시간
 */
public record ActionOutcome (
        IotAction action,
        ActionStatus status,
        Instant confirmedAt,
        boolean simulated,
        String error,
        Instant notifiedAt
){
    /** 룰 히트 제외 type일때만 */
    public static ActionOutcome none(){
        return new ActionOutcome(null, ActionStatus.NONE, null, false, null, Instant.now());
    }
}

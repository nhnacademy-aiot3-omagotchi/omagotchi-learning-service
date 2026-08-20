package site.omagotchi.learningservice.environment.domain;

import java.time.Instant;
import java.util.Objects;

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
    //컴팩트 생성자 검증
    public ActionOutcome {
        Objects.requireNonNull(status, "status가 null입니다.");

        switch (status) {
            case NONE -> require(Objects.isNull(action), "NONE은 action을 가질 수 없습니다.");
            case SKIPPED -> require(Objects.nonNull(action), "SKIPPED는 action이 필요합니다.");
            case CONFIRMED -> {
                require(Objects.nonNull(action), "CONFIRMED는 action이 필요합니다.");
                require(Objects.isNull(error), "CONFIRMED는 error를 가질 수 없습니다.");
            }
            case FAILED -> {
                require(Objects.nonNull(action), "FAILED는 action이 필요합니다.");
                require(Objects.isNull(confirmedAt), "FAILED는 confirmedAt을 가질 수 없습니다.");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 룰 히트 제외 type일때만 */
    public static ActionOutcome none(){

        return new ActionOutcome(null, ActionStatus.NONE, null, false, null, null);
    }

    /** 쿨다운에 걸려 명령을 보내지않을 때 */
    public static ActionOutcome skipped(IotAction action){
        return new ActionOutcome(action, ActionStatus.SKIPPED, null, false, null, null);
    }

    /** 제어기가 동작을 확인했을 때, notifiedAt은 발송 실패 시 null */
    public static ActionOutcome confirm(IotAction action, Instant confirmedAt, boolean simulated, Instant notifiedAt){
        return new ActionOutcome(action, ActionStatus.CONFIRMED, confirmedAt, simulated, null, notifiedAt);
    }

    /** 명려이 실패했거나 확인을 못 받았을 때 */
    public static ActionOutcome failed(IotAction action, String error, boolean simulated){
        return new ActionOutcome(action, ActionStatus.FAILED, null, simulated, error, null);
    }
}

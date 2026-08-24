package site.omagotchi.learningservice.environment.application.result;

import java.time.Instant;

/**
 * 제어기기의 조치 성공 여부
 *어
 * @param actioned 동작을 했는가 여부
 * @param at 요청을 받고 동작한 시각
 * @param simulated 시뮬레이터 응답인가. 현재는 시뮬밖에 없어서 항상 false
 * @param error 실패 이유
 */
public record IotActionResult (
        boolean actioned,
        Instant at,
        boolean simulated,
        String error
){
    public static IotActionResult failure(String error){
        return new IotActionResult(false, null, false, error);
    }

    public boolean succeeded(){
        return actioned && error == null;
    }
}

package site.omagotchi.learningservice.study.application.result;

import java.time.Instant;
import java.util.Map;

/**
 * 공간 하나의 시간대별 환경값.
 *
 * <p>{@code hourlyAverages}의 키는 정시(시간 경계)다. 센서 조회가 시간 단위로 평균을 내
 * 구간 시작 시각을 붙여 주므로, 학습 세션이 걸친 시간대를 이 키로 찾을 수 있다.</p>
 *
 * <p>기준치(임계값)는 담지 않는다. 공간별 기준 조회는 관리자 권한을 요구해서 학생이
 * 호출할 수 없기 때문이다. 대신 분석은 "이 사람 세션들 사이의 상대 비교"로 한다 —
 * 절대 기준을 우리가 새로 정의하지 않는다.</p>
 */
public record SpaceEnvironmentSeries(
        Long spaceId,
        String spaceName,
        String measurement,
        Map<Instant, Double> hourlyAverages
) {

    public boolean hasValues() {
        return !hourlyAverages.isEmpty();
    }
}

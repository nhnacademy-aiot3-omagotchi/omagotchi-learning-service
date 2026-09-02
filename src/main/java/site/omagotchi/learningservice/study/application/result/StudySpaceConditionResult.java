package site.omagotchi.learningservice.study.application.result;

import java.time.Instant;
import java.util.List;

/**
 * 지금 공부할 곳을 고를 때 필요한 공간별 상태.
 *
 * <p>"좋다·나쁘다"를 절대 기준으로 판정하지 않는다. 같은 시점의 공간들을 나란히 놓고
 * 환경값이 낮은 순으로 정렬해 돌려줄 뿐, 어디를 권할지는 받는 쪽이 정한다.</p>
 *
 * @param spaces 이산화탄소가 낮은(환기가 잘 된) 순. 값이 없는 공간은 뒤로 밀린다
 */
public record StudySpaceConditionResult(
        Status status,
        List<SpaceCondition> spaces
) {

    public enum Status {
        OK,
        NO_SPACE,       // 기수에 배정된 사용 가능한 공간이 없다
        NO_SENSOR_DATA  // 공간은 있는데 최근 환경값이 하나도 없다
    }

    /**
     * 공간 하나의 지금 상태.
     *
     * @param usageStatus AVAILABLE·OCCUPIED 등 사용 상태. 점유자가 누구인지는 담지 않는다
     * @param co2         가장 최근 시간대의 이산화탄소 평균(ppm). 없으면 null
     * @param temperature 같은 시간대의 온도 평균(섭씨). 없으면 null
     * @param humidity    같은 시간대의 상대습도 평균(%). 없으면 null
     * @param measuredAt  값이 속한 시간대의 시작 시각. 없으면 null
     */
    public record SpaceCondition(
            Long spaceId,
            String spaceName,
            String spaceType,
            String usageStatus,
            Double co2,
            Double temperature,
            Double humidity,
            Instant measuredAt
    ) {
    }

    public static StudySpaceConditionResult noSpace() {
        return new StudySpaceConditionResult(Status.NO_SPACE, List.of());
    }

    public static StudySpaceConditionResult noSensorData(List<SpaceCondition> spaces) {
        return new StudySpaceConditionResult(Status.NO_SENSOR_DATA, spaces);
    }
}

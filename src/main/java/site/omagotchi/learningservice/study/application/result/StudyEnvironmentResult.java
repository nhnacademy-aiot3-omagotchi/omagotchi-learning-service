package site.omagotchi.learningservice.study.application.result;

import java.util.List;

/**
 * 학습 세션을 "어디서 했는가"와 "그때 환경이 어땠는가"로 갈라 본 결과.
 *
 * <p>절대 기준치로 좋고 나쁨을 판정하지 않는다. 이 사람의 세션들 사이에서 환경값이
 * 높았던 쪽과 낮았던 쪽을 나눠 몰입 밀도를 비교한다 — 기준은 중앙값이다. 판정 기준은
 * 해석하는 쪽(도구 설명)이 갖는다.</p>
 *
 * @param analyzedSessionCount     공간과 환경값이 모두 확인된 세션 수
 * @param unknownSpaceSessionCount 체류 기록이 없어 공간을 알 수 없던 세션 수
 */
public record StudyEnvironmentResult(
        Status status,
        int periodDays,
        int analyzedSessionCount,
        int unknownSpaceSessionCount,
        List<SpacePerformance> spaces,
        List<MeasurementContrast> contrasts
) {

    public enum Status {
        OK,
        NO_DATA,          // 기간 내 학습 세션이 없다
        NO_SPACE_DATA,    // 세션은 있는데 공간이 붙은 세션이 없다
        NO_SENSOR_DATA    // 공간은 붙었는데 그 시간대 환경값이 없다
    }

    /** 공간 하나에서의 학습 성과와 그 시간대 평균 환경값. 값이 없는 항목은 null이다. */
    public record SpacePerformance(
            Long spaceId,
            String spaceName,
            int sessionCount,
            long totalStudyMinutes,
            int focusDensityPercent,
            Double averageCo2,
            Double averageTemperature,
            Double averageHumidity
    ) {
    }

    /** 한 측정항목에서, 값이 낮았던 세션과 높았던 세션의 몰입 밀도 대비. */
    public record MeasurementContrast(
            String measurement,
            double medianValue,
            int lowSessionCount,
            int lowFocusDensityPercent,
            double lowAverageValue,
            int highSessionCount,
            int highFocusDensityPercent,
            double highAverageValue
    ) {
    }

    public static StudyEnvironmentResult noData(int periodDays) {
        return new StudyEnvironmentResult(Status.NO_DATA, periodDays, 0, 0, List.of(), List.of());
    }

    public static StudyEnvironmentResult noSpaceData(int periodDays, int unknownSpaceSessionCount) {
        return new StudyEnvironmentResult(
                Status.NO_SPACE_DATA, periodDays, 0, unknownSpaceSessionCount, List.of(), List.of());
    }

    public static StudyEnvironmentResult noSensorData(
            int periodDays,
            int unknownSpaceSessionCount,
            List<SpacePerformance> spaces
    ) {
        return new StudyEnvironmentResult(
                Status.NO_SENSOR_DATA, periodDays, 0, unknownSpaceSessionCount, spaces, List.of());
    }
}

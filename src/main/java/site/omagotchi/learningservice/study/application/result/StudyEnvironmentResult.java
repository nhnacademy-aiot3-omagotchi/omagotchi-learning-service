package site.omagotchi.learningservice.study.application.result;

import java.util.List;

/**
 * 학습 세션을 "언제·어디서 했는가" 블록으로 쪼개, 블록마다 밀도와 환경값을 붙인 결과.
 *
 * <p>블록은 (집계일 × 시간대 × 공간)이다. 시간대는 집계일 원점인 새벽 4시에 맞춰 잘라
 * 한 블록이 두 조각으로 찢어지지 않는다.</p>
 *
 * <p>밀도의 분모는 그 블록에서 <b>첫 세션 시작~마지막 세션 종료</b>다. 세션 길이를 더하면
 * 세션 사이 쉰 시간이 빠져 항상 100%가 나오기 때문이다.</p>
 *
 * @param spaceSource 공간을 어떻게 알아냈는지. PRESENCE=체류 기록, COHORT_LAB=기수 실습실로
 *                    추정, NONE=알 수 없음. 추정이면 단정적으로 말하면 안 된다
 */
public record StudyEnvironmentResult(
        Status status,
        int periodDays,
        String spaceSource,
        int analyzedSessionCount,
        int unknownSpaceSessionCount,
        List<BlockSummary> timeBands,
        List<BlockSummary> spaces
) {

    public enum Status {
        OK,
        NO_DATA,          // 기간 내 학습 세션이 없다
        NO_SPACE_DATA,    // 세션은 있는데 공간을 하나도 알 수 없다
        NO_SENSOR_DATA    // 공간은 알지만 그 시간대 환경값이 없다
    }

    /**
     * 묶어 본 한 덩어리(시간대 또는 공간)의 학습 성과와 환경값.
     *
     * @param label       시간대면 "오후(13-18시)", 공간이면 공간 이름
     * @param spaceId     공간 묶음일 때만 값이 있다
     * @param spanMinutes 그 묶음에서 자리에 있던 시간(분)
     */
    public record BlockSummary(
            String label,
            Long spaceId,
            int sessionCount,
            long studyMinutes,
            long spanMinutes,
            int densityPercent,
            Double averageCo2,
            Double averageTemperature,
            Double averageHumidity
    ) {
    }

    public static StudyEnvironmentResult noData(int periodDays) {
        return new StudyEnvironmentResult(
                Status.NO_DATA, periodDays, "NONE", 0, 0, List.of(), List.of());
    }

    public static StudyEnvironmentResult noSpaceData(int periodDays, int unknownSpaceSessionCount) {
        return new StudyEnvironmentResult(
                Status.NO_SPACE_DATA, periodDays, "NONE", 0, unknownSpaceSessionCount,
                List.of(), List.of());
    }

    public static StudyEnvironmentResult noSensorData(
            int periodDays,
            String spaceSource,
            int analyzedSessionCount,
            int unknownSpaceSessionCount,
            List<BlockSummary> timeBands,
            List<BlockSummary> spaces
    ) {
        return new StudyEnvironmentResult(
                Status.NO_SENSOR_DATA, periodDays, spaceSource,
                analyzedSessionCount, unknownSpaceSessionCount, timeBands, spaces);
    }
}

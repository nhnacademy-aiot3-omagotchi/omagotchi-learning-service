package site.omagotchi.learningservice.space.application.result;

/**
 * 공간을 직접 사용 중인 인원과 회의 종료 후 돌아올 인원을 분리한 집계.
 */
public record SpacePresenceSummary(
        long currentCount,
        long returnReservationCount
) {

    public static SpacePresenceSummary empty() {
        return new SpacePresenceSummary(0L, 0L);
    }

    public long reservedCount() {
        return currentCount + returnReservationCount;
    }
}

package site.omagotchi.learningservice.study.application.result;

public record TopLearnerPatternResult(
        Status status,                 // OK냐 NO_DATA냐 INSUFFICIENT_SAMPLE냐
        int periodDays,                // 몇 일 기준의 결과인가
        int cohortStudentCount,        // 기수 학생 수 (표본 크기)
        int topGroupSize,              // 상위 그룹 인원
        long topAverageDailyMinutes,   // 상위 그룹: 공부한 날 하루 평균 분
        int topAverageStudyDayCount,   // 상위 그룹: 기간 중 평균 학습일 수
        long topAverageSessionMinutes, // 상위 그룹: 평균 세션 길이
        int topFocusDensityPercent,    // 상위 그룹: 몰입 밀도(앉은 시간 중 실제 공부 비율)
        String topTypicalStartTime,    // 상위 그룹: 대표 시작 시각 "HH:mm"
        StudyPatternResult myPattern   // 내 패턴 (비교용, 기존 그릇 재사용)
) {
    public enum Status { OK, INSUFFICIENT_SAMPLE, NO_DATA }

    // 학생 10명 미만
    public static TopLearnerPatternResult insufficientSample(int periodDays, int cohortStudentCount) {
        return new TopLearnerPatternResult(Status.INSUFFICIENT_SAMPLE, periodDays,
                cohortStudentCount, 0, 0, 0, 0, 0, null, null);
    }

    // 기수 전체가 기간 내 기록 없음
    public static TopLearnerPatternResult noData(int periodDays, int cohortStudentCount) {
        return new TopLearnerPatternResult(Status.NO_DATA, periodDays,
                cohortStudentCount, 0, 0, 0, 0, 0, null, null);
    }
}
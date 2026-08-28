package site.omagotchi.learningservice.study.application.result;

public record StudyPatternResult(
        Status status,              // OK냐 NO_DATA냐
        int periodDays,             // 몇 일 기준의 결과인가
        int studyDayCount,          // 기간 중 공부한 날 수
        long totalStudyMinutes,     // 총 공부 분
        int sessionCount,           // 세션 개수
        long averageSessionMinutes, // 평균 세션 길이
        long longestSessionMinutes, // 최장 세션
        String typicalStartTime,    // 하루 첫 세션 시작의 중앙값
        Integer bestStartHour,      // 공부량이 가장 많은 세션 시작 시각
        int currentStreakDays       // 연속 학습일
) {
    public enum Status { OK, NO_DATA }

    public static StudyPatternResult noData(int periodDays) {
        return new StudyPatternResult(Status.NO_DATA, periodDays,
                0, 0, 0, 0, 0, null, null, 0);
    }
}
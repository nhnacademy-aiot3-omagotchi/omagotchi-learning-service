package site.omagotchi.learningservice.study.application.result;

/**
 * 원본 세션을 읽지 않고 일별 집계만으로 만든 기간 학습 시간 요약
 */
public record StudyTimeSummaryResult(
        Status status,
        int periodDays,
        long totalStudyMinutes,
        int studyDayCount,
        long averageStudyMinutesPerStudyDay
) {
    public enum Status {
        OK, NO_DATA
    }

    public static StudyTimeSummaryResult noData(int periodDays) {
        return new StudyTimeSummaryResult(Status.NO_DATA, periodDays, 0, 0, 0);
    }
}

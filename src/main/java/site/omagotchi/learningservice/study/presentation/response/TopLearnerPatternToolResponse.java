package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;

public record TopLearnerPatternToolResponse(
        String status,
        int periodDays,
        int cohortStudentCount,
        int topGroupSize,
        long topAverageDailyMinutes,   // status가 OK일 때만 의미 있음
        int topAverageStudyDayCount,
        long topAverageSessionMinutes,
        int topFocusDensityPercent,    // 상위 그룹의 몰입 밀도
        String topTypicalStartTime,    // status가 OK일 때만 값이 있음
        StudyPatternToolResponse myPattern // status가 OK일 때만 값이 있음
) {
    public static TopLearnerPatternToolResponse from(TopLearnerPatternResult result) {
        StudyPatternToolResponse myPattern = null;
        if (result.myPattern() != null) {
            myPattern = StudyPatternToolResponse.from(result.myPattern());
        }

        return new TopLearnerPatternToolResponse(
                result.status().name(),
                result.periodDays(),
                result.cohortStudentCount(),
                result.topGroupSize(),
                result.topAverageDailyMinutes(),
                result.topAverageStudyDayCount(),
                result.topAverageSessionMinutes(),
                result.topFocusDensityPercent(),
                result.topTypicalStartTime(),
                myPattern
        );
    }
}
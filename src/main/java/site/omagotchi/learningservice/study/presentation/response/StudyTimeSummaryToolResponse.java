package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.StudyTimeSummaryResult;

public record StudyTimeSummaryToolResponse(
        String status,
        int periodDays,
        long totalStudyMinutes,
        int studyDayCount,
        long averageStudyMinutesPerStudyDay
) {
    public static StudyTimeSummaryToolResponse from(StudyTimeSummaryResult result) {
        return new StudyTimeSummaryToolResponse(
                result.status().name(),
                result.periodDays(),
                result.totalStudyMinutes(),
                result.studyDayCount(),
                result.averageStudyMinutesPerStudyDay()
        );
    }
}

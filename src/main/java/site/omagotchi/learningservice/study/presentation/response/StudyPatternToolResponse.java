package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.StudyPatternResult;

public record StudyPatternToolResponse(
        String status,
        int periodDays,
        int studyDayCount,
        long totalStudyMinutes,
        int sessionCount,
        long averageSessionMinutes,
        long longestSessionMinutes,
        String typicalStartTime, // status가 OK일 때만 값이 있음
        Integer bestStartHour,   // status가 OK일 때만 값이 있음
        int currentStreakDays
) {
    public static StudyPatternToolResponse from(StudyPatternResult result) {
        return new StudyPatternToolResponse(
                result.status().name(),
                result.periodDays(),
                result.studyDayCount(),
                result.totalStudyMinutes(),
                result.sessionCount(),
                result.averageSessionMinutes(),
                result.longestSessionMinutes(),
                result.typicalStartTime(),
                result.bestStartHour(),
                result.currentStreakDays()
        );
    }
}
package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.LearningReportResult;

public record LearningReportToolResponse(
        int periodDays,
        long previousTotalStudyMinutes,
        int previousStudyDayCount,
        TopLearnerPatternToolResponse thisPeriod,
        StudyEnvironmentToolResponse environment
) {
    public static LearningReportToolResponse from(LearningReportResult result) {
        return new LearningReportToolResponse(
                result.periodDays(),
                result.previousTotalStudyMinutes(),
                result.previousStudyDayCount(),
                TopLearnerPatternToolResponse.from(result.thisPeriod()),
                StudyEnvironmentToolResponse.from(result.environment())
        );
    }
}

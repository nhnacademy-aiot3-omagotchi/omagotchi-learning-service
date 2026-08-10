package site.omagotchi.learningservice.study.application.result;

public record StudyProfileSummaryResult(
        long totalStudySeconds,
        long completedSessionCount
) {
}

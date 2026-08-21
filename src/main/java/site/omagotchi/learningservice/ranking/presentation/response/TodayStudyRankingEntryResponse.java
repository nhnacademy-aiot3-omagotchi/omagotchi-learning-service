package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;

public record TodayStudyRankingEntryResponse(
        long rank,
        String displayName,
        long studySeconds,
        boolean timerRunning
) {

    public static TodayStudyRankingEntryResponse from(StudyRankingEntryResult result) {
        return new TodayStudyRankingEntryResponse(
                result.rank(),
                result.displayName(),
                result.studySeconds(),
                result.timerRunning()
        );
    }
}

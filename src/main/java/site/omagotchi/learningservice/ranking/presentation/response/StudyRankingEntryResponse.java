package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;

public record StudyRankingEntryResponse(
        long rank,
        String displayName,
        long studySeconds
) {

    public static StudyRankingEntryResponse from(StudyRankingEntryResult result) {
        return new StudyRankingEntryResponse(
                result.rank(),
                result.displayName(),
                result.studySeconds()
        );
    }
}

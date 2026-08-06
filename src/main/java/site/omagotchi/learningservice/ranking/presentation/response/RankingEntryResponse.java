package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.RankingEntryResult;

public record RankingEntryResponse(
        int rank,
        String displayName,
        long studySeconds
) {

    public static RankingEntryResponse from(RankingEntryResult result) {
        return new RankingEntryResponse(
                result.rank(),
                result.displayName(),
                result.studySeconds()
        );
    }
}

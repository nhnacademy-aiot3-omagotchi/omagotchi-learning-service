package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;

public record MyStudyRankingResponse(
        long rankedMemberCount,
        boolean ranked,
        StudyRankingEntryResponse ranking
) {

    public static MyStudyRankingResponse from(MyStudyRankingResult result) {
        return new MyStudyRankingResponse(
                result.rankedMemberCount(),
                result.ranked(),
                result.ranking()
                        .map(StudyRankingEntryResponse::from)
                        .orElse(null)
        );
    }
}

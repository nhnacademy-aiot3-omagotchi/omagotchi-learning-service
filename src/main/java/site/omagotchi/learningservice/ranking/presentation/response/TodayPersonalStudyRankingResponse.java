package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;

public record TodayPersonalStudyRankingResponse(
        boolean ranked,
        TodayStudyRankingEntryResponse ranking
) {

    public static TodayPersonalStudyRankingResponse from(MyStudyRankingResult result) {
        return new TodayPersonalStudyRankingResponse(
                result.ranked(),
                result.ranking()
                        .map(TodayStudyRankingEntryResponse::from)
                        .orElse(null)
        );
    }
}

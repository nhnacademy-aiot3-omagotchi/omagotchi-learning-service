package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;

public record PersonalStudyRankingResponse(
        boolean ranked,
        StudyRankingEntryResponse ranking
) {

    public static PersonalStudyRankingResponse from(MyStudyRankingResult result) {
        return new PersonalStudyRankingResponse(
                result.ranked(),
                result.ranking()
                        .map(StudyRankingEntryResponse::from)
                        .orElse(null)
        );
    }
}

package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;

import java.time.LocalDate;
import java.util.List;

public record MemberStudyRankingResponse(
        LocalDate startDate,
        LocalDate includedThroughDate,
        long rankedMemberCount,
        int returnedEntryCount,
        List<StudyRankingEntryResponse> entries,
        PersonalStudyRankingResponse myRanking
) {

    public static MemberStudyRankingResponse from(
            HistoricalStudyRankingResult<MemberStudyRankingViewResult> result
    ) {
        StudyRankingBoardResult board = result.ranking().board();
        List<StudyRankingEntryResponse> entries = board.entries().stream()
                .map(StudyRankingEntryResponse::from)
                .toList();
        return new MemberStudyRankingResponse(
                result.startDate(),
                result.includedThroughDate().orElse(null),
                board.rankedMemberCount(),
                entries.size(),
                entries,
                PersonalStudyRankingResponse.from(result.ranking().mine())
        );
    }
}

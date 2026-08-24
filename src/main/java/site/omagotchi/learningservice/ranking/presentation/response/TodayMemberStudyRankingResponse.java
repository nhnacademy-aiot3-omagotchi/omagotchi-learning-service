package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.TodayStudyRankingResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TodayMemberStudyRankingResponse(
        LocalDate aggregationDate,
        Instant calculatedAt,
        long rankedMemberCount,
        int returnedEntryCount,
        List<TodayStudyRankingEntryResponse> entries,
        TodayPersonalStudyRankingResponse myRanking
) {

    public static TodayMemberStudyRankingResponse from(
            TodayStudyRankingResult<MemberStudyRankingViewResult> result
    ) {
        StudyRankingBoardResult board = result.ranking().board();
        List<TodayStudyRankingEntryResponse> entries = board.entries().stream()
                .map(TodayStudyRankingEntryResponse::from)
                .toList();
        return new TodayMemberStudyRankingResponse(
                result.aggregationDate(),
                result.calculatedAt(),
                board.rankedMemberCount(),
                entries.size(),
                entries,
                TodayPersonalStudyRankingResponse.from(result.ranking().mine())
        );
    }
}

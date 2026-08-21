package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.TodayStudyRankingResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TodayTeamStudyRankingResponse(
        LocalDate aggregationDate,
        Instant calculatedAt,
        long rankedTeamCount,
        int returnedEntryCount,
        List<TeamStudyRankingEntryResponse> entries,
        MyTeamStudyRankingResponse myTeamRanking
) {

    // 오늘 팀 랭킹 결과에 집계일과 계산 기준 시각을 포함해 HTTP 응답으로 변환한다.
    public static TodayTeamStudyRankingResponse from(
            TodayStudyRankingResult<TeamStudyRankingViewResult> result
    ) {
        TeamStudyRankingBoardResult board = result.ranking().board();
        List<TeamStudyRankingEntryResponse> entries = board.entries().stream()
                .map(TeamStudyRankingEntryResponse::from)
                .toList();
        return new TodayTeamStudyRankingResponse(
                result.aggregationDate(),
                result.calculatedAt(),
                board.rankedTeamCount(),
                entries.size(),
                entries,
                MyTeamStudyRankingResponse.from(result.ranking().mine())
        );
    }
}

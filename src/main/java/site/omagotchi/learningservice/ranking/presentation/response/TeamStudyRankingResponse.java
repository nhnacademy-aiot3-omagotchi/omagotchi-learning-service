package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingViewResult;

import java.time.LocalDate;
import java.util.List;

public record TeamStudyRankingResponse(
        LocalDate startDate,
        LocalDate includedThroughDate,
        long rankedTeamCount,
        int returnedEntryCount,
        List<TeamStudyRankingEntryResponse> entries,
        MyTeamStudyRankingResponse myTeamRanking
) {

    // 과거 팀 랭킹 결과의 기간과 순위 행을 HTTP 응답 형식으로 변환한다.
    public static TeamStudyRankingResponse from(
            HistoricalStudyRankingResult<TeamStudyRankingViewResult> result
    ) {
        TeamStudyRankingBoardResult board = result.ranking().board();
        List<TeamStudyRankingEntryResponse> entries = board.entries().stream()
                .map(TeamStudyRankingEntryResponse::from)
                .toList();
        return new TeamStudyRankingResponse(
                result.startDate(),
                result.includedThroughDate().orElse(null),
                board.rankedTeamCount(),
                entries.size(),
                entries,
                MyTeamStudyRankingResponse.from(result.ranking().mine())
        );
    }
}

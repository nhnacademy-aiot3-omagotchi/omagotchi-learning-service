package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.MyTeamStudyRankingResult;

public record MyTeamStudyRankingResponse(
        boolean ranked,
        TeamStudyRankingEntryResponse ranking
) {

    // 요청자의 팀 순위 존재 여부와 선택적 순위 행을 HTTP 응답으로 변환한다.
    public static MyTeamStudyRankingResponse from(MyTeamStudyRankingResult result) {
        return new MyTeamStudyRankingResponse(
                result.ranked(),
                result.ranking()
                        .map(TeamStudyRankingEntryResponse::from)
                        .orElse(null)
        );
    }
}

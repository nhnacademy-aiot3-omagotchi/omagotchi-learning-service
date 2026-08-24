package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingEntryResult;

public record TeamStudyRankingEntryResponse(
        long rank,
        Long teamId,
        String teamName,
        long studySeconds
) {

    // Application의 팀 순위 한 행을 외부 응답 필드로 그대로 옮긴다.
    public static TeamStudyRankingEntryResponse from(TeamStudyRankingEntryResult result) {
        return new TeamStudyRankingEntryResponse(
                result.rank(),
                result.teamId(),
                result.teamName(),
                result.studySeconds()
        );
    }
}

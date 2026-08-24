package site.omagotchi.learningservice.ranking.application.result;

import java.util.List;

public record TeamStudyRankingBoardResult(
        long rankedTeamCount,
        List<TeamStudyRankingEntryResult> entries
) {

    // 생성 이후 외부 변경으로 순위 목록이 달라지지 않도록 방어적 복사한다.
    public TeamStudyRankingBoardResult {
        entries = List.copyOf(entries);
    }
}

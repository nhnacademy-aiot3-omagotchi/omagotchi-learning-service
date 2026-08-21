package site.omagotchi.learningservice.ranking.application.result;

import java.util.Optional;

public record MyTeamStudyRankingResult(
        Optional<TeamStudyRankingEntryResult> ranking
) {

    // 요청자의 현재 팀이 양수 공부시간으로 순위에 포함됐는지 알려준다.
    public boolean ranked() {
        return ranking.isPresent();
    }
}

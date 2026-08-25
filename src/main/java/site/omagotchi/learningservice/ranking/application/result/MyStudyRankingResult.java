package site.omagotchi.learningservice.ranking.application.result;

import java.util.Optional;

public record MyStudyRankingResult(
        long rankedMemberCount,
        Optional<StudyRankingEntryResult> ranking
) {

    public boolean ranked() {
        return ranking.isPresent();
    }
}

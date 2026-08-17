package site.omagotchi.learningservice.ranking.application.result;

import java.util.Objects;
import java.util.Optional;

public record MyStudyRankingResult(
        long rankedMemberCount,
        Optional<StudyRankingEntryResult> ranking
) {

    public MyStudyRankingResult {
        Objects.requireNonNull(ranking, "ranking must not be null");
    }

    public boolean ranked() {
        return ranking.isPresent();
    }
}

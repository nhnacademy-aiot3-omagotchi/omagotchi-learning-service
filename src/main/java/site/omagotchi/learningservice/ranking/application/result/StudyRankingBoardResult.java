package site.omagotchi.learningservice.ranking.application.result;

import java.util.List;

public record StudyRankingBoardResult(
        long rankedMemberCount,
        List<StudyRankingEntryResult> entries
) {

    public StudyRankingBoardResult {
        entries = List.copyOf(entries);
    }
}

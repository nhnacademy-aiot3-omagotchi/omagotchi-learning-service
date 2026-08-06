package site.omagotchi.learningservice.ranking.application.result;

import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;

import java.util.UUID;

public record RankingEntryResult(
        int rank,
        UUID userId,
        Long userCharacterId,
        String displayName,
        long studySeconds
) {

    public static RankingEntryResult from(RankingSnapshotEntry entry) {
        return new RankingEntryResult(
                entry.getRank(),
                entry.getUserId(),
                entry.getUserCharacterId(),
                entry.getDisplayName(),
                entry.getStudySeconds()
        );
    }
}

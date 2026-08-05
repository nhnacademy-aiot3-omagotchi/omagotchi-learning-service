package site.omagotchi.learningservice.ranking.domain;

import java.util.UUID;

public record RankingRankedEntry(
        int rank,
        UUID userId,
        Long userCharacterId,
        String displayName,
        long studySeconds
) {
}

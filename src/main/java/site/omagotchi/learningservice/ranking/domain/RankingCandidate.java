package site.omagotchi.learningservice.ranking.domain;

import java.util.UUID;

public record RankingCandidate(
        UUID userId,
        Long userCharacterId,
        String displayName,
        long studySeconds
) {
}

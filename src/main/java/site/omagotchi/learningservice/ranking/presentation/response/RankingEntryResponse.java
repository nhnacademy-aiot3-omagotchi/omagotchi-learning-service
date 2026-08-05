package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.RankingEntryResult;

import java.util.UUID;

public record RankingEntryResponse(
        int rank,
        UUID userId,
        Long userCharacterId,
        String displayName,
        long studySeconds
) {

    public static RankingEntryResponse from(RankingEntryResult result) {
        return new RankingEntryResponse(
                result.rank(),
                result.userId(),
                result.userCharacterId(),
                result.displayName(),
                result.studySeconds()
        );
    }
}

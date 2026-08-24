package site.omagotchi.learningservice.ranking.application.query;

import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

public record StudyRankingQuery(
        Integer maxRank
) {

    public static final int DEFAULT_MAX_RANK = 100;
    public static final int MAX_MAX_RANK = 1_000;

    public int resolveMaxRank() {
        int resolvedMaxRank = maxRank == null ? DEFAULT_MAX_RANK : maxRank;
        if (resolvedMaxRank < 1 || resolvedMaxRank > MAX_MAX_RANK) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return resolvedMaxRank;
    }
}

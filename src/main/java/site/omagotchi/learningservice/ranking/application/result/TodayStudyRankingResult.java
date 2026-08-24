package site.omagotchi.learningservice.ranking.application.result;

import java.time.Instant;
import java.time.LocalDate;

public record TodayStudyRankingResult<T>(
        LocalDate aggregationDate,
        Instant calculatedAt,
        T ranking
) {
}

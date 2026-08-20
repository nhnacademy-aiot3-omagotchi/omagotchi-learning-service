package site.omagotchi.learningservice.ranking.application.result;

import java.time.LocalDate;
import java.util.Optional;

public record HistoricalStudyRankingResult<T>(
        LocalDate startDate,
        Optional<LocalDate> includedThroughDate,
        T ranking
) {
}

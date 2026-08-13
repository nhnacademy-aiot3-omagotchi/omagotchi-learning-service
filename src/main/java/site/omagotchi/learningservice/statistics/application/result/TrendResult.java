package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TrendResult(
        String window,
        LocalDate from,
        LocalDate to,
        Instant calculatedAt,
        long totalStudySeconds,
        long averageDailyStudySeconds,
        List<DailyTotalResult> dailyTotals
) {

    public TrendResult {
        dailyTotals = List.copyOf(dailyTotals);
    }
}

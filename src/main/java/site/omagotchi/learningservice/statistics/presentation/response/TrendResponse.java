package site.omagotchi.learningservice.statistics.presentation.response;

import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TrendResponse(
        String window,
        LocalDate from,
        LocalDate to,
        Instant calculatedAt,
        long totalStudySeconds,
        long averageDailyStudySeconds,
        List<DailyTotal> dailyTotals
) {

    public TrendResponse {
        dailyTotals = List.copyOf(dailyTotals);
    }

    public static TrendResponse from(TrendResult result) {
        return new TrendResponse(
                result.window(),
                result.from(),
                result.to(),
                result.calculatedAt(),
                result.totalStudySeconds(),
                result.averageDailyStudySeconds(),
                result.dailyTotals().stream()
                        .map(DailyTotal::from)
                        .toList()
        );
    }

    public record DailyTotal(
            LocalDate aggregationDate,
            long studySeconds
    ) {

        private static DailyTotal from(DailyTotalResult result) {
            return new DailyTotal(result.aggregationDate(), result.studySeconds());
        }
    }
}

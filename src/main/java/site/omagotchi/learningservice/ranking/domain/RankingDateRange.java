package site.omagotchi.learningservice.ranking.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record RankingDateRange(
        LocalDate startDate,
        LocalDate endDate
) {

    public static RankingDateRange from(RankingPeriod period, LocalDate baseDate) {
        return switch (period) {
            case DAILY -> new RankingDateRange(baseDate, baseDate);
            case WEEKLY -> new RankingDateRange(
                    baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    baseDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case MONTHLY -> new RankingDateRange(
                    baseDate.withDayOfMonth(1),
                    baseDate.withDayOfMonth(baseDate.lengthOfMonth())
            );
        };
    }
}

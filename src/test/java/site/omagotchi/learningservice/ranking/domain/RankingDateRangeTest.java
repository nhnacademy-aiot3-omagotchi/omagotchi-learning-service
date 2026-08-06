package site.omagotchi.learningservice.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("랭킹 기간 계산")
class RankingDateRangeTest {

    @Test
    @DisplayName("DAILY/WEEKLY/MONTHLY 기간을 계산한다")
    void calculatesPeriodRanges() {
        LocalDate baseDate = LocalDate.of(2026, 8, 5);

        RankingDateRange daily = RankingDateRange.from(RankingPeriod.DAILY, baseDate);
        RankingDateRange weekly = RankingDateRange.from(RankingPeriod.WEEKLY, baseDate);
        RankingDateRange monthly = RankingDateRange.from(RankingPeriod.MONTHLY, baseDate);

        assertAll(
                () -> assertEquals(LocalDate.of(2026, 8, 5), daily.startDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 5), daily.endDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 3), weekly.startDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 9), weekly.endDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 1), monthly.startDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 31), monthly.endDate())
        );
    }
}

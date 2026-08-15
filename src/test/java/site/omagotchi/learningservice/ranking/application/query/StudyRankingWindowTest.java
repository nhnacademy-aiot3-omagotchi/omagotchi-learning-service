package site.omagotchi.learningservice.ranking.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("학습 랭킹 조회 기간")
class StudyRankingWindowTest {

    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "DAILY, 2000-01-13, 2000-01-13",
            "WEEKLY, 2000-01-10, 2000-01-13",
            "MONTHLY, 2000-01-01, 2000-01-13"
    })
    @DisplayName("현재 집계일 기준 일간 주간 월간 범위 정상 처리")
    void resolvesCurrentPeriod(
            StudyRankingPeriod period,
            LocalDate expectedStartDate,
            LocalDate expectedEndDate
    ) {
        StudyRankingWindow window = StudyRankingWindow.resolve(
                period,
                CALCULATED_AT
        );

        assertAll(
                () -> assertEquals(expectedStartDate, window.startDate()),
                () -> assertEquals(expectedEndDate, window.endDate())
        );
    }
}

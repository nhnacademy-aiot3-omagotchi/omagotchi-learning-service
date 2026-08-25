package site.omagotchi.learningservice.ranking.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("학습 랭킹 조회 기간")
class StudyRankingWindowTest {

    private static final LocalDate CURRENT_AGGREGATION_DATE = LocalDate.parse("2000-01-13");

    @Nested
    @DisplayName("일간")
    class Daily {

        @Test
        @DisplayName("과거 집계일 정상 처리")
        void resolvesPastDate() {
            LocalDate requestedDate = LocalDate.parse("2000-01-12");

            StudyRankingWindow window = StudyRankingWindow.daily(
                    requestedDate,
                    CURRENT_AGGREGATION_DATE
            );

            assertAll(
                    () -> assertEquals(requestedDate, window.startDate()),
                    () -> assertEquals(Optional.of(requestedDate), window.includedThroughDate())
            );
        }

        @Test
        @DisplayName("현재 집계일 요청 예외")
        void rejectsCurrentDate() {
            assertInvalidRequest(() -> StudyRankingWindow.daily(
                    CURRENT_AGGREGATION_DATE,
                    CURRENT_AGGREGATION_DATE
            ));
        }
    }

    @Nested
    @DisplayName("주간")
    class Weekly {

        @Test
        @DisplayName("과거 주는 일요일까지 정상 처리")
        void resolvesPastWeek() {
            StudyRankingWindow window = StudyRankingWindow.weekly(
                    LocalDate.parse("2000-01-03"),
                    CURRENT_AGGREGATION_DATE
            );

            assertAll(
                    () -> assertEquals(LocalDate.parse("2000-01-03"), window.startDate()),
                    () -> assertEquals(
                            Optional.of(LocalDate.parse("2000-01-09")),
                            window.includedThroughDate()
                    )
            );
        }

        @Test
        @DisplayName("현재 주는 현재 집계일 전날까지 정상 처리")
        void resolvesCurrentWeekThroughYesterday() {
            StudyRankingWindow window = StudyRankingWindow.weekly(
                    LocalDate.parse("2000-01-10"),
                    CURRENT_AGGREGATION_DATE
            );

            assertEquals(
                    Optional.of(LocalDate.parse("2000-01-12")),
                    window.includedThroughDate()
            );
        }

        @Test
        @DisplayName("현재 주 월요일은 확정 집계일 없음 처리")
        void returnsEmptyClosedDateOnCurrentMonday() {
            LocalDate monday = LocalDate.parse("2000-01-10");

            StudyRankingWindow window = StudyRankingWindow.weekly(monday, monday);

            assertEquals(Optional.empty(), window.includedThroughDate());
        }

        @Test
        @DisplayName("월요일이 아닌 시작일 예외")
        void rejectsNonMonday() {
            assertInvalidRequest(() -> StudyRankingWindow.weekly(
                    LocalDate.parse("2000-01-11"),
                    CURRENT_AGGREGATION_DATE
            ));
        }
    }

    @Nested
    @DisplayName("월간")
    class Monthly {

        @Test
        @DisplayName("과거 월은 말일까지 정상 처리")
        void resolvesPastMonth() {
            StudyRankingWindow window = StudyRankingWindow.monthly(
                    YearMonth.parse("1999-12"),
                    CURRENT_AGGREGATION_DATE
            );

            assertAll(
                    () -> assertEquals(LocalDate.parse("1999-12-01"), window.startDate()),
                    () -> assertEquals(
                            Optional.of(LocalDate.parse("1999-12-31")),
                            window.includedThroughDate()
                    )
            );
        }

        @Test
        @DisplayName("현재 월은 현재 집계일 전날까지 정상 처리")
        void resolvesCurrentMonthThroughYesterday() {
            StudyRankingWindow window = StudyRankingWindow.monthly(
                    YearMonth.parse("2000-01"),
                    CURRENT_AGGREGATION_DATE
            );

            assertEquals(
                    Optional.of(LocalDate.parse("2000-01-12")),
                    window.includedThroughDate()
            );
        }

        @Test
        @DisplayName("현재 월 1일은 확정 집계일 없음 처리")
        void returnsEmptyClosedDateOnFirstDay() {
            StudyRankingWindow window = StudyRankingWindow.monthly(
                    YearMonth.parse("2000-01"),
                    LocalDate.parse("2000-01-01")
            );

            assertEquals(Optional.empty(), window.includedThroughDate());
        }

        @Test
        @DisplayName("미래 월 예외")
        void rejectsFutureMonth() {
            assertInvalidRequest(() -> StudyRankingWindow.monthly(
                    YearMonth.parse("2000-02"),
                    CURRENT_AGGREGATION_DATE
            ));
        }
    }

    private void assertInvalidRequest(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }
}

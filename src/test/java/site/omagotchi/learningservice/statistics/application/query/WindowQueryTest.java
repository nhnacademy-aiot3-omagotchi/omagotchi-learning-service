package site.omagotchi.learningservice.statistics.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("공부 통계 조회 window")
class WindowQueryTest {

    @Nested
    @DisplayName("기간 해석")
    class ResolvePeriod {

        @Test
        @DisplayName("KST 04시 직전 7일 하한 정상 처리")
        void resolvesSevenDayWindowBeforeFourAmBoundary() {
            WindowQuery.DateRange period = WindowQuery
                    .parse("7d")
                    .resolveAt(Instant.parse("2000-01-07T18:59:59Z"));

            assertAll(
                    () -> assertEquals("7d", period.window().value()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 1), period.from()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 7), period.to())
            );
        }

        @Test
        @DisplayName("KST 04시부터 새 집계일 정상 처리")
        void usesNewAggregationDateFromFourAmBoundary() {
            WindowQuery.DateRange period = WindowQuery
                    .parse("7d")
                    .resolveAt(Instant.parse("2000-01-07T19:00:00Z"));

            assertAll(
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 2), period.from()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 8), period.to())
            );
        }

        @Test
        @DisplayName("14일 중간값 정상 처리")
        void resolvesFourteenDayWindow() {
            WindowQuery.DateRange period = WindowQuery
                    .parse("14d")
                    .resolveAt(Instant.parse("2000-01-14T03:00:00Z"));

            assertEquals(LocalDate.of(2000, Month.JANUARY, 1), period.from());
        }

        @Test
        @DisplayName("60일 상한 정상 처리")
        void resolvesSixtyDayWindow() {
            WindowQuery.DateRange period = WindowQuery
                    .parse("60d")
                    .resolveAt(Instant.parse("2000-02-28T19:00:00Z"));

            assertAll(
                    () -> assertEquals(60, period.window().days()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 1), period.from())
            );
        }
    }

    @Nested
    @DisplayName("요청값 검증")
    class ValidateRequest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"6d", "61d", "7", "07d", "7D", " 7d"})
        @DisplayName("지원하지 않는 window 요청값 예외")
        void rejectsUnsupportedWindow(String value) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> WindowQuery.parse(value)
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("계산 결과 검증")
    class ValidateDateRange {

        private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 7);

        @Test
        @DisplayName("필수값 누락 예외")
        void rejectsMissingRequiredValue() {
            WindowQuery window = new WindowQuery(7);

            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new WindowQuery.DateRange(null, BASE_DATE, BASE_DATE)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new WindowQuery.DateRange(window, null, BASE_DATE)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new WindowQuery.DateRange(window, BASE_DATE, null)
                    )
            );
        }

        @Test
        @DisplayName("window와 날짜 범위 불일치 예외")
        void rejectsRangeThatDoesNotMatchWindow() {
            WindowQuery window = new WindowQuery(7);

            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new WindowQuery.DateRange(
                                    window,
                                    BASE_DATE,
                                    BASE_DATE.minusDays(6)
                            )
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new WindowQuery.DateRange(
                                    window,
                                    BASE_DATE.minusDays(5),
                                    BASE_DATE
                            )
                    )
            );
        }
    }
}

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

@DisplayName("학습 통계 조회 기간")
class WindowQueryTest {

    @Nested
    @DisplayName("조회 기간 계산")
    class ResolveDateRange {

        @Test
        @DisplayName("KST 04시 직전 7일 하한 정상 처리")
        void resolvesSevenDayWindowBeforeFourAmBoundary() {
            WindowQuery.DateRange dateRange = WindowQuery
                    .parse("7d")
                    .resolveAt(Instant.parse("2000-01-07T18:59:59Z"));

            assertAll(
                    () -> assertEquals("7d", dateRange.window().value()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 1), dateRange.from()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 7), dateRange.to())
            );
        }

        @Test
        @DisplayName("KST 04시부터 새 집계일 정상 처리")
        void usesNewAggregationDateFromFourAmBoundary() {
            WindowQuery.DateRange dateRange = WindowQuery
                    .parse("7d")
                    .resolveAt(Instant.parse("2000-01-07T19:00:00Z"));

            assertAll(
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 2), dateRange.from()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 8), dateRange.to())
            );
        }

        @Test
        @DisplayName("14일 중간값 정상 처리")
        void resolvesFourteenDayWindow() {
            WindowQuery.DateRange dateRange = WindowQuery
                    .parse("14d")
                    .resolveAt(Instant.parse("2000-01-14T03:00:00Z"));

            assertEquals(LocalDate.of(2000, Month.JANUARY, 1), dateRange.from());
        }

        @Test
        @DisplayName("60일 상한 정상 처리")
        void resolvesSixtyDayWindow() {
            WindowQuery.DateRange dateRange = WindowQuery
                    .parse("60d")
                    .resolveAt(Instant.parse("2000-02-28T19:00:00Z"));

            assertAll(
                    () -> assertEquals(60, dateRange.window().days()),
                    () -> assertEquals(LocalDate.of(2000, Month.JANUARY, 1), dateRange.from())
            );
        }

        @Test
        @DisplayName("기준 시각 누락 예외")
        void rejectsMissingCurrentInstant() {
            WindowQuery window = new WindowQuery(7);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> window.resolveAt(null)
            );
        }
    }

    @Nested
    @DisplayName("요청값 검증")
    class ValidateRequest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"6d", "61d", "7", "07d", "7D", " 7d"})
        @DisplayName("지원하지 않는 조회 기간 요청값 예외")
        void rejectsUnsupportedWindow(String value) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> WindowQuery.parse(value)
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }

        @ParameterizedTest
        @ValueSource(ints = {6, 61})
        @DisplayName("지원하지 않는 조회 일수 예외")
        void rejectsUnsupportedDays(int days) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> new WindowQuery(days)
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("계산 결과 불변식")
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
        @DisplayName("조회 기간과 날짜 범위 불일치 예외")
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

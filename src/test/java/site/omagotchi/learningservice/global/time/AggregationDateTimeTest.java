package site.omagotchi.learningservice.global.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("공통 집계 시간 기준")
class AggregationDateTimeTest {

    @Nested
    @DisplayName("한국 시각 변환")
    class ToInstant {

        @Test
        @DisplayName("한국 날짜와 시각을 Instant로 변환")
        void convertsSeoulLocalTimeToInstant() {
            Instant instant = AggregationDateTime.toInstant(
                    LocalDate.parse("2000-01-01"),
                    LocalTime.of(10, 30)
            );

            assertEquals(Instant.parse("2000-01-01T01:30:00Z"), instant);
        }

        @Test
        @DisplayName("한국 일시를 Instant로 변환")
        void convertsSeoulLocalDateTimeToInstant() {
            Instant instant = AggregationDateTime.toInstant(
                    LocalDateTime.parse("2000-01-01T23:30")
            );

            assertEquals(Instant.parse("2000-01-01T14:30:00Z"), instant);
        }
    }

    @Nested
    @DisplayName("집계일 계산")
    class AggregationDate {

        @Test
        @DisplayName("KST 04시 직전은 전날 집계일")
        void returnsPreviousDateBeforeResetBoundary() {
            LocalDate aggregationDate = AggregationDateTime.aggregationDate(
                    Instant.parse("2000-01-01T18:59:59Z")
            );

            assertEquals(LocalDate.parse("2000-01-01"), aggregationDate);
        }

        @Test
        @DisplayName("KST 04시부터 당일 집계일")
        void returnsCurrentDateFromResetBoundary() {
            LocalDate aggregationDate = AggregationDateTime.aggregationDate(
                    Instant.parse("2000-01-01T19:00:00Z")
            );

            assertEquals(LocalDate.parse("2000-01-02"), aggregationDate);
        }

        @Test
        @DisplayName("집계일 시작은 KST 04시")
        void returnsStartOfAggregationDate() {
            Instant start = AggregationDateTime.startOfAggregationDate(
                    LocalDate.parse("2000-01-02")
            );

            assertEquals(Instant.parse("2000-01-01T19:00:00Z"), start);
        }
    }
}

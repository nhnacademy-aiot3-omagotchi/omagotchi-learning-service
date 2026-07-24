package site.omagotchi.learningservice.global.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@DisplayName("날짜 및 시간")
class DateTimeProviderTest {

    private static final Instant FIXED_NOW = Instant.parse("2000-01-01T01:30:00Z");

    private DateTimeProvider dateTimeProvider;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        dateTimeProvider = new DateTimeProvider(clock);
    }

    @Nested
    @DisplayName("현재 시각 조회")
    class CurrentInstant {

        @Test
        @DisplayName("정상 처리")
        void returnsCurrentInstantFromUtcClock() {
            Instant currentTime = dateTimeProvider.currentInstant();

            log.info("- 현재 시각 -");
            log.info("결과: {}", currentTime);

            assertEquals(FIXED_NOW, currentTime);
        }
    }

    @Nested
    @DisplayName("지역 시각 변환")
    class ConvertToZonedDateTime {

        @Test
        @DisplayName("정상 처리")
        void convertsInstantToSeoulZonedDateTime() {
            Instant instant = Instant.parse("2000-01-01T00:00:00Z");

            ZonedDateTime zonedDateTime = dateTimeProvider.toZonedDateTime(instant);

            log.info("- 지역 시간 변화 -");
            log.info("입력: {}", instant);
            log.info("결과: {}", zonedDateTime);

            assertEquals(
                    ZonedDateTime.parse("2000-01-01T09:00:00+09:00[Asia/Seoul]"),
                    zonedDateTime
            );
        }
    }

    @Nested
    @DisplayName("집계 기준일 계산")
    class CalculateAggregationDate {

        @Test
        @DisplayName("기준 시각 이전은 전날 귀속")
        void assignsTimeBeforeResetToPreviousDate() {
            Instant instant = Instant.parse("2000-01-01T18:59:59Z");

            LocalDate aggregationDate = dateTimeProvider.calculateAggregationDate(instant);

            log.info("- 기준 시간 계산 -");
            log.info("입력: {}", instant);
            log.info("결과: {}", aggregationDate);

            assertEquals(LocalDate.of(2000, Month.JANUARY, 1), aggregationDate);
        }

        @Test
        @DisplayName("기준 시각부터 당일 귀속")
        void assignsResetBoundaryToCurrentDate() {
            Instant instant = Instant.parse("2000-01-01T19:00:00Z");

            LocalDate aggregationDate = dateTimeProvider.calculateAggregationDate(instant);

            log.info("- 기준 시간 계산 -");
            log.info("입력: {}", instant);
            log.info("결과: {}", aggregationDate);

            assertEquals(LocalDate.of(2000, Month.JANUARY, 2), aggregationDate);
        }
    }

    @Nested
    @DisplayName("집계 구간 계산")
    class CalculateAggregationWindow {

        @Test
        @DisplayName("정상 처리")
        void calculatesAggregationDateWindowAsInstants() {
            LocalDate aggregationDate = LocalDate.of(2000, Month.JANUARY, 10);

            Instant start = dateTimeProvider.startOfAggregationDate(aggregationDate);
            Instant endExclusive = dateTimeProvider.endExclusiveOfAggregationDate(aggregationDate);

            log.info("- 기준 시작 시간 계산 -");
            log.info("입력: {}", aggregationDate);
            log.info("기준 시작 시간: {}", start);
            log.info("기준 종료 시간: {}", endExclusive);

            assertAll(
                    () -> assertEquals(
                            Instant.parse("2000-01-09T19:00:00Z"),
                            start
                    ),
                    () -> assertEquals(
                            Instant.parse("2000-01-10T19:00:00Z"),
                            endExclusive
                    )
            );
        }
    }

    @Nested
    @DisplayName("집계 경계 교차 판정")
    class AggregationBoundaryCrossing {

        @Test
        @DisplayName("종료 시각이 04시 경계와 같으면 미교차")
        void doesNotCrossWhenEndTimeEqualsBoundary() {
            Instant startTime = Instant.parse("2000-01-01T18:59:00Z");
            Instant endTime = Instant.parse("2000-01-01T19:00:00Z");

            boolean crosses = dateTimeProvider.crossesAggregationBoundary(startTime, endTime);

            assertFalse(crosses);
        }

        @Test
        @DisplayName("04시 경계를 넘으면 교차")
        void crossesWhenIntervalContinuesAfterBoundary() {
            Instant startTime = Instant.parse("2000-01-01T18:59:00Z");
            Instant endTime = Instant.parse("2000-01-01T19:01:00Z");

            boolean crosses = dateTimeProvider.crossesAggregationBoundary(startTime, endTime);

            assertTrue(crosses);
        }
    }
}

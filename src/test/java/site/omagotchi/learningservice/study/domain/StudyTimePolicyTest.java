package site.omagotchi.learningservice.study.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("학습 시간 정책")
class StudyTimePolicyTest {

    @Nested
    @DisplayName("분 단위 정규화")
    class MinuteNormalization {

        @Test
        @DisplayName("시작 내림과 종료 올림")
        void normalizesStartAndEndToMinutes() {
            Instant instant = Instant.parse("2000-01-01T00:00:30Z");

            Instant startTime = StudyTimePolicy.floorToMinute(instant);
            Instant endTime = StudyTimePolicy.ceilToMinute(instant);

            assertAll(
                    () -> assertEquals(Instant.parse("2000-01-01T00:00:00Z"), startTime),
                    () -> assertEquals(Instant.parse("2000-01-01T00:01:00Z"), endTime),
                    () -> assertTrue(StudyTimePolicy.isMinuteAligned(startTime)),
                    () -> assertTrue(StudyTimePolicy.isMinuteAligned(endTime)),
                    () -> assertTrue(StudyTimePolicy.isMinuteAligned(LocalTime.of(10, 0))),
                    () -> assertFalse(StudyTimePolicy.isMinuteAligned(LocalTime.of(10, 0, 1)))
            );
        }
    }

    @Nested
    @DisplayName("집계 경계 교차 판정")
    class AggregationBoundaryCrossing {

        @Test
        @DisplayName("종료 시각이 KST 04시 경계와 같으면 미교차")
        void doesNotCrossWhenEndTimeEqualsBoundary() {
            Instant startTime = Instant.parse("2000-01-01T18:59:00Z");
            Instant endTime = Instant.parse("2000-01-01T19:00:00Z");

            boolean crosses = StudyTimePolicy.crossesAggregationBoundary(startTime, endTime);

            assertFalse(crosses);
        }

        @Test
        @DisplayName("KST 04시 경계를 넘으면 교차")
        void crossesWhenIntervalContinuesAfterBoundary() {
            Instant startTime = Instant.parse("2000-01-01T18:59:00Z");
            Instant endTime = Instant.parse("2000-01-01T19:01:00Z");

            boolean crosses = StudyTimePolicy.crossesAggregationBoundary(startTime, endTime);

            assertTrue(crosses);
        }

        @Test
        @DisplayName("교차한 KST 04시 경계 반환")
        void returnsCrossedBoundary() {
            Instant startTime = Instant.parse("2000-01-01T18:59:00Z");
            Instant endTime = Instant.parse("2000-01-01T19:01:00Z");

            Instant boundary = StudyTimePolicy
                    .findCrossedAggregationBoundary(startTime, endTime)
                    .orElseThrow();

            assertEquals(Instant.parse("2000-01-01T19:00:00Z"), boundary);
        }

        @Test
        @DisplayName("미교차 시 경계 없음")
        void returnsEmptyWhenBoundaryIsNotCrossed() {
            Instant startTime = Instant.parse("2000-01-01T18:59:00Z");
            Instant endTime = Instant.parse("2000-01-01T19:00:00Z");

            boolean empty = StudyTimePolicy
                    .findCrossedAggregationBoundary(startTime, endTime)
                    .isEmpty();

            assertTrue(empty);
        }
    }
}

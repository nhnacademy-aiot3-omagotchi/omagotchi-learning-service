package site.omagotchi.learningservice.study.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("공부 기록")
class StudyRecordTest {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final Instant START_TIME = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant END_TIME = Instant.parse("2000-01-01T02:00:00Z");

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 처리")
        void createsValidRecord() {
            StudyRecord studyRecord = StudyRecord.create(
                    COHORT_MEMBERSHIP_ID,
                    START_TIME,
                    END_TIME,
                    1_800L
            );

            assertAll(
                    () -> assertEquals(COHORT_MEMBERSHIP_ID, studyRecord.getCohortMembershipId()),
                    () -> assertEquals(BASE_DATE, studyRecord.getAggregationDate()),
                    () -> assertEquals(START_TIME, studyRecord.getStartTime()),
                    () -> assertEquals(END_TIME, studyRecord.getEndTime()),
                    () -> assertEquals(1_800L, studyRecord.getStudySeconds())
            );
        }

        @Test
        @DisplayName("필수값 누락 예외")
        void rejectsMissingRequiredValues() {
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(null, START_TIME, END_TIME, 3_600L)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(COHORT_MEMBERSHIP_ID, null, END_TIME, 3_600L)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(COHORT_MEMBERSHIP_ID, START_TIME, null, 3_600L)
                    )
            );
        }

        @Test
        @DisplayName("잘못된 시간 범위 예외")
        void rejectsInvalidTimeRange() {
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(
                                    COHORT_MEMBERSHIP_ID,
                                    START_TIME,
                                    START_TIME,
                                    1L
                            )
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(
                                    COHORT_MEMBERSHIP_ID,
                                    END_TIME,
                                    START_TIME,
                                    1L
                            )
                    )
            );
        }

        @Test
        @DisplayName("초 정밀도 기록 허용")
        void acceptsSecondAlignedTime() {
            StudyRecord studyRecord = StudyRecord.create(
                    COHORT_MEMBERSHIP_ID,
                    START_TIME.plusSeconds(1),
                    END_TIME,
                    3_599L
            );

            assertAll(
                    () -> assertEquals(START_TIME.plusSeconds(1), studyRecord.getStartTime()),
                    () -> assertEquals(END_TIME, studyRecord.getEndTime()),
                    () -> assertEquals(3_599L, studyRecord.getStudySeconds())
            );
        }

        @Test
        @DisplayName("초 미만 정밀도 위반 예외")
        void rejectsSubSecondPrecision() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StudyRecord.create(
                            COHORT_MEMBERSHIP_ID,
                            START_TIME,
                            END_TIME.plusNanos(1),
                            3_600L
                    )
            );
        }

        @Test
        @DisplayName("잘못된 공부 시간 예외")
        void rejectsInvalidStudySeconds() {
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(
                                    COHORT_MEMBERSHIP_ID,
                                    START_TIME,
                                    END_TIME,
                                    0L
                            )
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(
                                    COHORT_MEMBERSHIP_ID,
                                    START_TIME,
                                    END_TIME,
                                    3_601L
                            )
                    )
            );
        }

        @Test
        @DisplayName("04시 집계 경계 반개구간 적용")
        void appliesHalfOpenAggregationBoundary() {
            Instant startTime = Instant.parse("1999-12-31T18:59:00Z");
            Instant boundary = Instant.parse("1999-12-31T19:00:00Z");

            StudyRecord studyRecord = StudyRecord.create(
                    COHORT_MEMBERSHIP_ID,
                    startTime,
                    boundary,
                    60L
            );

            assertAll(
                    () -> assertEquals(
                            LocalDate.of(1999, Month.DECEMBER, 31),
                            studyRecord.getAggregationDate()
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> StudyRecord.create(
                                    COHORT_MEMBERSHIP_ID,
                                    startTime,
                                    boundary.plusSeconds(60),
                                    120L
                            )
                    )
            );
        }
    }

    @Nested
    @DisplayName("시간 범위 수정")
    class UpdateTimeRange {

        @Test
        @DisplayName("정상 처리")
        void updatesTimeRange() {
            StudyRecord studyRecord = createRecord();
            Instant startTime = Instant.parse("2000-01-02T01:00:00Z");
            Instant endTime = Instant.parse("2000-01-02T03:00:00Z");

            studyRecord.updateTimeRange(startTime, endTime, 7_200L);

            assertAll(
                    () -> assertEquals(
                            LocalDate.of(2000, Month.JANUARY, 2),
                            studyRecord.getAggregationDate()
                    ),
                    () -> assertEquals(startTime, studyRecord.getStartTime()),
                    () -> assertEquals(endTime, studyRecord.getEndTime()),
                    () -> assertEquals(7_200L, studyRecord.getStudySeconds())
            );
        }

        @Test
        @DisplayName("실패 시 기존 상태 유지")
        void keepsStateWhenUpdateFails() {
            StudyRecord studyRecord = createRecord();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> studyRecord.updateTimeRange(END_TIME, START_TIME, 3_600L)
            );

            assertAll(
                    () -> assertEquals(BASE_DATE, studyRecord.getAggregationDate()),
                    () -> assertEquals(START_TIME, studyRecord.getStartTime()),
                    () -> assertEquals(END_TIME, studyRecord.getEndTime()),
                    () -> assertEquals(3_600L, studyRecord.getStudySeconds())
            );
        }
    }

    @Nested
    @DisplayName("논리 삭제")
    class SoftDelete {

        @Test
        @DisplayName("정상 처리")
        void softDeletesRecord() {
            StudyRecord studyRecord = createRecord();
            Instant deletedAt = Instant.parse("2000-01-01T03:00:00Z");

            studyRecord.softDelete(deletedAt);

            assertEquals(deletedAt, studyRecord.getDeletedAt());
        }

        @Test
        @DisplayName("삭제 시각 누락 예외")
        void rejectsMissingDeletedAt() {
            StudyRecord studyRecord = createRecord();

            assertThrows(IllegalArgumentException.class, () -> studyRecord.softDelete(null));

            assertNull(studyRecord.getDeletedAt());
        }

        @Test
        @DisplayName("삭제 후 변경 예외")
        void rejectsChangesAfterDeletion() {
            StudyRecord studyRecord = createRecord();
            Instant deletedAt = Instant.parse("2000-01-01T03:00:00Z");
            studyRecord.softDelete(deletedAt);

            assertAll(
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> studyRecord.updateTimeRange(START_TIME, END_TIME, 3_600L)
                    ),
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> studyRecord.softDelete(deletedAt.plusSeconds(1))
                    )
            );
        }
    }

    private StudyRecord createRecord() {
        return StudyRecord.create(
                COHORT_MEMBERSHIP_ID,
                START_TIME,
                END_TIME,
                3_600L
        );
    }
}

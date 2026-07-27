package site.omagotchi.learningservice.study.infrastructure.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("study_records Entity")
class StudyRecordEntityTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final Instant START_TIME = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant END_TIME = Instant.parse("2000-01-01T02:00:00Z");

    @Test
    @DisplayName("시간 및 집계일 갱신")
    void testApplyUpdate() {
        StudyRecordEntity entity = createEntity();
        LocalDate aggregationDate = LocalDate.of(2000, Month.JANUARY, 2);
        Instant startTime = Instant.parse("2000-01-02T01:00:00Z");
        Instant endTime = Instant.parse("2000-01-02T03:00:00Z");

        entity.applyUpdate(aggregationDate, startTime, endTime, 7_200L);

        assertAll(
                () -> assertEquals(aggregationDate, entity.getAggregationDate()),
                () -> assertEquals(startTime, entity.getStartTime()),
                () -> assertEquals(endTime, entity.getEndTime()),
                () -> assertEquals(7_200L, entity.getStudySeconds())
        );
    }

    @Test
    @DisplayName("논리 삭제 처리")
    void testSoftDelete() {
        StudyRecordEntity entity = createEntity();
        Instant deletedAt = Instant.parse("2000-01-01T03:00:00Z");

        entity.applySoftDelete(deletedAt);

        assertEquals(deletedAt, entity.getDeletedAt());
    }

    private StudyRecordEntity createEntity() {
        return StudyRecordEntity.builder()
                .cohortMembershipId(1L)
                .aggregationDate(BASE_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .studySeconds(3_600L)
                .build();
    }
}

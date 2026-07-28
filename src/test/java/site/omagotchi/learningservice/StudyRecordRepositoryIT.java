package site.omagotchi.learningservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.study.domain.entity.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@DisplayName("학습 기록 저장소")
class StudyRecordRepositoryIT {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    @Nested
    @DisplayName("활성 기록 겹침 조회")
    class ActiveOverlap {

        @Test
        @DisplayName("실제 교집합 조회")
        void findsIntersectingInterval() {
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );

            boolean overlaps = studyRecordRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T01:30:00Z"),
                    Instant.parse("2000-01-01T02:30:00Z"),
                    null
            );

            assertTrue(overlaps);
        }

        @Test
        @DisplayName("맞닿은 반개구간 제외")
        void excludesTouchingBoundary() {
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );

            boolean overlaps = studyRecordRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T02:00:00Z"),
                    Instant.parse("2000-01-01T03:00:00Z"),
                    null
            );

            assertFalse(overlaps);
        }

        @Test
        @DisplayName("삭제 기록 제외")
        void excludesDeletedRecord() {
            StudyRecord studyRecord = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );
            studyRecord.applySoftDelete(Instant.parse("2000-01-02T00:00:00Z"));
            studyRecordRepository.saveAndFlush(studyRecord);

            boolean overlaps = studyRecordRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T01:30:00Z"),
                    Instant.parse("2000-01-01T02:30:00Z"),
                    null
            );

            assertFalse(overlaps);
        }

        @Test
        @DisplayName("다른 소속 기록 제외")
        void excludesOtherMembershipRecord() {
            saveRecord(
                    2L,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );

            boolean overlaps = studyRecordRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T01:30:00Z"),
                    Instant.parse("2000-01-01T02:30:00Z"),
                    null
            );

            assertFalse(overlaps);
        }

        @Test
        @DisplayName("수정 대상 자신 제외")
        void excludesUpdatedRecordItself() {
            StudyRecord studyRecord = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );

            boolean overlaps = studyRecordRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T01:00:00Z"),
                    Instant.parse("2000-01-01T02:00:00Z"),
                    studyRecord.getId()
            );

            assertFalse(overlaps);
        }

        @Test
        @DisplayName("수정 대상 외 다른 겹침 기록 조회")
        void findsOtherOverlappingRecordAfterExclusion() {
            StudyRecord updatedRecord = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T03:00:00Z",
                    "2000-01-01T04:00:00Z"
            );

            boolean overlaps = studyRecordRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T03:30:00Z"),
                    Instant.parse("2000-01-01T04:30:00Z"),
                    updatedRecord.getId()
            );

            assertTrue(overlaps);
        }
    }

    @Nested
    @DisplayName("활성 기록 겹침 제약")
    class ActiveOverlapConstraint {

        @Test
        @DisplayName("같은 소속의 겹친 기록 저장 거절")
        void rejectsOverlappingRecordsForSameCohortMembership() {
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> saveRecord(
                            COHORT_MEMBERSHIP_ID,
                            "2000-01-01T01:30:00Z",
                            "2000-01-01T02:30:00Z"
                    )
            );
        }
    }

    @Nested
    @DisplayName("낙관적 버전")
    class OptimisticVersion {

        @Test
        @DisplayName("변경 성공 시 버전 증가")
        void incrementsVersionAfterSuccessfulUpdate() {
            StudyRecord studyRecord = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );
            studyRecord.applyUpdate(
                    BASE_DATE,
                    Instant.parse("2000-01-01T03:00:00Z"),
                    Instant.parse("2000-01-01T04:00:00Z"),
                    3_600L
            );

            StudyRecord updated = studyRecordRepository.saveAndFlush(studyRecord);

            assertEquals(1L, updated.getVersion());
        }
    }

    private StudyRecord saveRecord(
            Long cohortMembershipId,
            String startTime,
            String endTime
    ) {
        Instant startInstant = Instant.parse(startTime);
        Instant endInstant = Instant.parse(endTime);
        StudyRecord studyRecord = StudyRecord.builder()
                .cohortMembershipId(cohortMembershipId)
                .aggregationDate(BASE_DATE)
                .startTime(startInstant)
                .endTime(endInstant)
                .studySeconds(endInstant.getEpochSecond() - startInstant.getEpochSecond())
                .build();

        return studyRecordRepository.saveAndFlush(studyRecord);
    }
}

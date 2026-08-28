package site.omagotchi.learningservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordQueryDslRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        StudyRecordQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("학습 기록 저장소")
class StudyRecordRepositoryIT {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);

    @Autowired
    private StudyRecordJpaRepository studyRecordRepository;

    @Autowired
    private StudyRecordQueryDslRepository studyRecordQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMemberships() {
        CohortMembershipTestFixture.ensureActiveMemberships(
                jdbcTemplate,
                COHORT_MEMBERSHIP_ID,
                2L,
                101L
        );
    }

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

            boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
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

            boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
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
            studyRecord.softDelete(Instant.parse("2000-01-02T00:00:00Z"));
            studyRecordRepository.saveAndFlush(studyRecord);

            boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
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

            boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
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

            boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
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

            boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    Instant.parse("2000-01-01T03:30:00Z"),
                    Instant.parse("2000-01-01T04:30:00Z"),
                    updatedRecord.getId()
            );

            assertTrue(overlaps);
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
            studyRecord.updateTimeRange(
                    Instant.parse("2000-01-01T03:00:00Z"),
                    Instant.parse("2000-01-01T04:00:00Z"),
                    3_600L
            );

            StudyRecord updated = studyRecordRepository.saveAndFlush(studyRecord);

            assertEquals(1L, updated.getVersion());
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
    @DisplayName("시간 정밀도 제약")
    class TimePrecisionConstraint {

        @Test
        @DisplayName("초 미만 정밀도 저장 거절")
        void rejectsSubSecondPrecision() {
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update("""
                                    INSERT INTO learning_service.study_records (
                                        id,
                                        cohort_membership_id,
                                        aggregation_date,
                                        start_time,
                                        end_time,
                                        study_seconds
                                    ) VALUES (?, ?, ?, ?, ?, ?)
                                    """,
                            UUID.fromString("00000000-0000-0000-0000-000000000101"),
                            101L,
                            BASE_DATE,
                            OffsetDateTime.parse("2000-01-01T01:00:00.001Z"),
                            OffsetDateTime.parse("2000-01-01T02:00:00Z"),
                            3_600L
                    )
            );
        }

        @Test
        @DisplayName("초 정밀도 저장 허용")
        void allowsSecondPrecision() {
            StudyRecord saved = saveRecord(
                    101L,
                    "2000-01-01T01:00:01Z",
                    "2000-01-01T02:00:01Z"
            );

            assertAll(
                    () -> assertEquals(
                            Instant.parse("2000-01-01T01:00:01Z"),
                            saved.getStartTime()
                    ),
                    () -> assertEquals(
                            Instant.parse("2000-01-01T02:00:01Z"),
                            saved.getEndTime()
                    )
            );
        }
    }

    @Nested
    @DisplayName("소속 FK 제약")
    class MembershipConstraint {

        @Test
        @DisplayName("존재하지 않는 소속의 기록 저장 거절")
        void rejectsUnknownMembership() {
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update("""
                                    INSERT INTO learning_service.study_records (
                                        id,
                                        cohort_membership_id,
                                        aggregation_date,
                                        start_time,
                                        end_time,
                                        study_seconds
                                    ) VALUES (?, ?, ?, ?, ?, ?)
                                    """,
                            UUID.fromString("00000000-0000-0000-0000-000000000102"),
                            102L,
                            BASE_DATE,
                            OffsetDateTime.parse("2000-01-01T01:00:00Z"),
                            OffsetDateTime.parse("2000-01-01T02:00:00Z"),
                            3_600L
                    )
            );
        }
    }

    private StudyRecord saveRecord(
            Long cohortMembershipId,
            String startTime,
            String endTime
    ) {
        Instant startInstant = Instant.parse(startTime);
        Instant endInstant = Instant.parse(endTime);
        StudyRecord studyRecord = StudyRecord.create(
                cohortMembershipId,
                startInstant,
                endInstant,
                endInstant.getEpochSecond() - startInstant.getEpochSecond()
        );

        return studyRecordRepository.saveAndFlush(studyRecord);
    }
}

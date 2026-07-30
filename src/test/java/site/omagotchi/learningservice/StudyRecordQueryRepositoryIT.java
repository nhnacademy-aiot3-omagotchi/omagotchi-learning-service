package site.omagotchi.learningservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordQueryDslRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        StudyRecordQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QueryDSL 학습 기록 조회 저장소")
class StudyRecordQueryRepositoryIT {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);

    @Autowired
    private StudyRecordJpaRepository studyRecordRepository;

    @Autowired
    private StudyRecordQueryDslRepository studyRecordQueryRepository;

    @Nested
    @DisplayName("활성 기록 단건 조회")
    class ActiveRecord {

        @Test
        @DisplayName("소속이 소유한 활성 기록 반환")
        void returnsActiveRecordOwnedByMembership() {
            StudyRecord studyRecord = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );

            StudyRecord result = studyRecordQueryRepository
                    .findActiveByIdAndCohortMembershipId(
                            studyRecord.getId(),
                            COHORT_MEMBERSHIP_ID
                    )
                    .orElseThrow();

            assertEquals(studyRecord.getId(), result.getId());
        }
    }

    @Nested
    @DisplayName("일간 활성 기록 조회")
    class DailyRecords {

        @Test
        @DisplayName("소속과 집계일이 일치하는 활성 기록만 시간순 반환")
        void returnsOnlyMatchingActiveRecordsInTimeOrder() {
            StudyRecord later = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T03:00:00Z",
                    "2000-01-01T04:00:00Z"
            );
            StudyRecord earlier = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );
            saveRecord(
                    2L,
                    BASE_DATE,
                    "2000-01-01T05:00:00Z",
                    "2000-01-01T06:00:00Z"
            );
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE.plusDays(1),
                    "2000-01-02T01:00:00Z",
                    "2000-01-02T02:00:00Z"
            );
            StudyRecord deleted = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T07:00:00Z",
                    "2000-01-01T08:00:00Z"
            );
            deleted.applySoftDelete(Instant.parse("2000-01-03T00:00:00Z"));
            studyRecordRepository.saveAndFlush(deleted);

            List<StudyRecord> result = studyRecordQueryRepository.findDailyRecords(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE
            );

            assertAll(
                    () -> assertEquals(2, result.size()),
                    () -> assertEquals(earlier.getId(), result.getFirst().getId()),
                    () -> assertEquals(later.getId(), result.getLast().getId())
            );
        }
    }

    @Nested
    @DisplayName("날짜별 공부 시간 집계")
    class DailyStudySecondsAggregation {

        @Test
        @DisplayName("조회 범위에 활성 기록이 없으면 빈 목록 반환")
        void returnsEmptyListWhenNoActiveRecordExistsInRequestedDateRange() {
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE.minusDays(1),
                    "1999-12-31T01:00:00Z",
                    "1999-12-31T02:00:00Z"
            );
            StudyRecord deleted = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );
            deleted.applySoftDelete(Instant.parse("2000-01-02T00:00:00Z"));
            studyRecordRepository.saveAndFlush(deleted);

            List<DailyStudySecondsResult> result = studyRecordQueryRepository.findDailyStudySeconds(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    BASE_DATE.plusDays(1)
            );

            assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("조회 범위의 활성 기록만 날짜별 합산")
        void sumsOnlyActiveRecordsInRequestedDateRange() {
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T01:00:00Z",
                    "2000-01-01T02:00:00Z"
            );
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    "2000-01-01T03:00:00Z",
                    "2000-01-01T05:00:00Z"
            );
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE.plusDays(1),
                    "2000-01-02T01:00:00Z",
                    "2000-01-02T01:30:00Z"
            );
            saveRecord(
                    2L,
                    BASE_DATE,
                    "2000-01-01T06:00:00Z",
                    "2000-01-01T07:00:00Z"
            );
            StudyRecord deleted = saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE.plusDays(1),
                    "2000-01-02T02:00:00Z",
                    "2000-01-02T03:00:00Z"
            );
            deleted.applySoftDelete(Instant.parse("2000-01-03T00:00:00Z"));
            studyRecordRepository.saveAndFlush(deleted);
            saveRecord(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE.plusDays(2),
                    "2000-01-03T01:00:00Z",
                    "2000-01-03T02:00:00Z"
            );

            List<DailyStudySecondsResult> result = studyRecordQueryRepository.findDailyStudySeconds(
                    COHORT_MEMBERSHIP_ID,
                    BASE_DATE,
                    BASE_DATE.plusDays(1)
            );

            assertEquals(List.of(
                    new DailyStudySecondsResult(BASE_DATE, 10_800L),
                    new DailyStudySecondsResult(BASE_DATE.plusDays(1), 1_800L)
            ), result);
        }
    }

    private StudyRecord saveRecord(
            Long cohortMembershipId,
            LocalDate aggregationDate,
            String startTime,
            String endTime
    ) {
        Instant startInstant = Instant.parse(startTime);
        Instant endInstant = Instant.parse(endTime);
        StudyRecord studyRecord = StudyRecord.builder()
                .cohortMembershipId(cohortMembershipId)
                .aggregationDate(aggregationDate)
                .startTime(startInstant)
                .endTime(endInstant)
                .studySeconds(endInstant.getEpochSecond() - startInstant.getEpochSecond())
                .build();

        return studyRecordRepository.saveAndFlush(studyRecord);
    }
}

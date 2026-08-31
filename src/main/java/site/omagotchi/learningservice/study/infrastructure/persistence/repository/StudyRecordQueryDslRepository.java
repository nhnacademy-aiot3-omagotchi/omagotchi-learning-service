package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.StudyProfileSummaryResult;
import site.omagotchi.learningservice.study.domain.QStudyRecord;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 공부 기록의 복합 조회를 QueryDSL로 제공한다.
 *
 * <p>활성 기록은 {@code deletedAt IS NULL}인 기록을 의미한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class StudyRecordQueryDslRepository implements StudyRecordQueryRepository {

    private static final QStudyRecord studyRecord = QStudyRecord.studyRecord;

    private final JPAQueryFactory queryFactory;

    /*
     * SELECT sr.*
     * FROM learning_service.study_records sr
     * WHERE sr.id = :studyRecordId
     *   AND sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL;
     */
    @Override
    public Optional<StudyRecord> findActiveByIdAndCohortMembershipId(
            UUID studyRecordId,
            Long cohortMembershipId
    ) {
        StudyRecord result = queryFactory
                .selectFrom(studyRecord)
                .where(
                        studyRecord.id.eq(studyRecordId),
                        activeRecordOfMembership(cohortMembershipId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /*
     * SELECT 1
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.start_time < :endTime
     *   AND sr.end_time > :startTime
     *   AND sr.id <> :excludedStudyRecordId -- excludedStudyRecordId가 null이 아닐 때만 추가
     * FETCH FIRST 1 ROW ONLY;
     */
    @Override
    public boolean existsActiveOverlap(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            UUID excludedStudyRecordId
    ) {
        Integer result = queryFactory
                .selectOne()
                .from(studyRecord)
                .where(
                        activeRecordOfMembership(cohortMembershipId),
                        studyRecord.startTime.lt(endTime),
                        studyRecord.endTime.gt(startTime),
                        excludeStudyRecord(excludedStudyRecordId)
                )
                .fetchFirst();

        return result != null;
    }

    /*
     * SELECT sr.*
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date = :aggregationDate
     * ORDER BY sr.start_time ASC,
     *          sr.id ASC;
     */
    @Override
    public List<StudyRecord> findDailyRecords(
            Long cohortMembershipId,
            LocalDate aggregationDate
    ) {
        return queryFactory
                .selectFrom(studyRecord)
                .where(
                        activeRecordOfMembership(cohortMembershipId),
                        studyRecord.aggregationDate.eq(aggregationDate)
                )
                .orderBy(
                        studyRecord.startTime.asc(),
                        studyRecord.id.asc()
                )
                .fetch();
    }

    /*
     * SELECT sr.aggregation_date,
     *        COALESCE(SUM(sr.study_seconds), 0) AS total_study_seconds
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date BETWEEN :startDate AND :endDateInclusive
     * GROUP BY sr.aggregation_date
     * ORDER BY sr.aggregation_date ASC;
     */
    @Override
    public List<DailyStudySecondsResult> findDailyStudySeconds(
            Long cohortMembershipId,
            LocalDate startDate,
            LocalDate endDateInclusive
    ) {
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);

        return queryFactory
                .select(Projections.constructor(
                        DailyStudySecondsResult.class,
                        studyRecord.aggregationDate,
                        totalStudySeconds
                ))
                .from(studyRecord)
                .where(
                        activeRecordOfMembership(cohortMembershipId),
                        studyRecord.aggregationDate.between(startDate, endDateInclusive)
                )
                .groupBy(studyRecord.aggregationDate)
                .orderBy(studyRecord.aggregationDate.asc())
                .fetch();
    }

    /*
     * SELECT sr.cohort_membership_id,
     *        COALESCE(SUM(sr.study_seconds), 0) AS total_study_seconds
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id IN (:cohortMembershipIds)
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date BETWEEN :startDate AND :endDateInclusive
     * GROUP BY sr.cohort_membership_id
     * HAVING COALESCE(SUM(sr.study_seconds), 0) > 0
     * ORDER BY sr.cohort_membership_id ASC;
     */
    @Override
    public List<MemberStudyDurationResult> findConfirmedDurations(
            Collection<Long> cohortMembershipIds,
            LocalDate startDate,
            LocalDate endDateInclusive
    ) {
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);

        return queryFactory
                .select(Projections.constructor(
                        MemberStudyDurationResult.class,
                        studyRecord.cohortMembershipId,
                        totalStudySeconds
                ))
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.in(cohortMembershipIds),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(startDate, endDateInclusive)
                )
                .groupBy(studyRecord.cohortMembershipId)
                .having(totalStudySeconds.gt(0L))
                .orderBy(studyRecord.cohortMembershipId.asc())
                .fetch();
    }

    /*
     * SELECT COALESCE(SUM(sr.study_seconds), 0) AS total_study_seconds,
     *        COUNT(sr.id) AS completed_session_count
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL;
     */
    @Override
    public StudyProfileSummaryResult summarizeActiveRecords(Long cohortMembershipId) {
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        NumberExpression<Long> completedSessionCount = studyRecord.id.count();

        var tuple = queryFactory
                .select(
                        totalStudySeconds,
                        completedSessionCount
                )
                .from(studyRecord)
                .where(activeRecordOfMembership(cohortMembershipId))
                .fetchOne();

        if (tuple == null) {
            return new StudyProfileSummaryResult(0L, 0L);
        }

        return new StudyProfileSummaryResult(
                tuple.get(totalStudySeconds) == null ? 0L : tuple.get(totalStudySeconds),
                tuple.get(completedSessionCount) == null ? 0L : tuple.get(completedSessionCount)
        );
    }

    /*
     * SELECT sr.*
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.aggregation_date BETWEEN :startDate AND :endDateInclusive
     *   AND sr.deleted_at IS NULL
     * ORDER BY sr.start_time;
     */
    @Override
    public List<StudyRecord> findActiveRecordsBetween(
            Long cohortMembershipId,
            LocalDate startDate,
            LocalDate endDateInclusive
    ) {
        return queryFactory
                .selectFrom(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.aggregationDate.between(startDate, endDateInclusive),
                        studyRecord.deletedAt.isNull()
                )
                .orderBy(studyRecord.startTime.asc())
                .fetch();
    }

    /*
     * SELECT sr.*
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id IN (:cohortMembershipIds)
     *   AND sr.aggregation_date BETWEEN :startDate AND :endDateInclusive
     *   AND sr.deleted_at IS NULL;
     */
    @Override
    public List<StudyRecord> findActiveRecordsBetweenForMemberships(
            Collection<Long> cohortMembershipIds,
            LocalDate startDate,
            LocalDate endDateInclusive
    ) {
        return queryFactory
                .selectFrom(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.in(cohortMembershipIds),
                        studyRecord.aggregationDate.between(startDate, endDateInclusive),
                        studyRecord.deletedAt.isNull()
                )
                .fetch();
    }

    private BooleanExpression activeRecordOfMembership(Long cohortMembershipId) {
        return studyRecord.cohortMembershipId.eq(cohortMembershipId)
                .and(studyRecord.deletedAt.isNull());
    }

    private BooleanExpression excludeStudyRecord(UUID excludedStudyRecordId) {
        return excludedStudyRecordId == null
                ? null
                : studyRecord.id.ne(excludedStudyRecordId);
    }
}

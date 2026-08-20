package site.omagotchi.learningservice.statistics.infrastructure.persistence.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.QCohortMembership;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery.SortDirection;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery.SortField;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.study.domain.QStudyRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberStatisticsQueryDslRepository
        implements MemberStatisticsRepository {

    private static final QCohortMembership cohortMembership = QCohortMembership.cohortMembership;
    private static final QStudyRecord studyRecord = QStudyRecord.studyRecord;

    private final JPAQueryFactory queryFactory;

    /*
     SELECT cm.id,
            cm.user_id,
            COALESCE(SUM(
                CASE WHEN sr.aggregation_date = :currentAggregationDate
                     THEN sr.study_seconds
                     ELSE 0
                END
            ), 0) AS today_study_seconds,
            COALESCE(SUM(sr.study_seconds), 0) AS period_study_seconds,
            COUNT(DISTINCT sr.aggregation_date) AS active_study_days,
            COUNT(sr.id) AS record_count,
            MAX(sr.end_time) AS last_studied_at
     FROM learning_service.cohort_memberships cm
     LEFT JOIN learning_service.study_records sr
       ON sr.cohort_membership_id = cm.id
      AND sr.deleted_at IS NULL
      AND sr.aggregation_date BETWEEN :from AND :to
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL
     GROUP BY cm.id, cm.user_id
     ORDER BY {selected_sort_expression} {ASC|DESC} NULLS LAST,
              cm.id ASC
     OFFSET :offset
     LIMIT :size;
     */
    @Override
    public List<MemberSummaryResult> findActiveStudentStatisticsPage(
            Long cohortId,
            LocalDate currentAggregationDate,
            LocalDate from,
            LocalDate to,
            MemberPageQuery query
    ) {
        NumberExpression<Long> todayStudySeconds = new CaseBuilder()
                .when(studyRecord.aggregationDate.eq(currentAggregationDate))
                .then(studyRecord.studySeconds)
                .otherwise(0L)
                .sumLong()
                .coalesce(0L);
        NumberExpression<Long> periodStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        NumberExpression<Long> activeStudyDays = studyRecord.aggregationDate.countDistinct();
        NumberExpression<Long> recordCount = studyRecord.id.count();
        DateTimeExpression<Instant> lastStudiedAt = studyRecord.endTime.max();

        return queryFactory
                .select(Projections.constructor(
                        MemberSummaryResult.class,
                        cohortMembership.id,
                        cohortMembership.userId,
                        todayStudySeconds,
                        periodStudySeconds,
                        activeStudyDays,
                        recordCount,
                        lastStudiedAt
                ))
                .from(cohortMembership)
                .leftJoin(studyRecord)
                .on(
                        studyRecord.cohortMembershipId.eq(cohortMembership.id),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(from, to)
                )
                .where(activeStudentOfCohort(cohortId))
                .groupBy(
                        cohortMembership.id,
                        cohortMembership.userId
                )
                .orderBy(
                        primaryOrder(
                                query.sortField(),
                                query.sortDirection(),
                                todayStudySeconds,
                                periodStudySeconds,
                                activeStudyDays,
                                recordCount,
                                lastStudiedAt
                        ),
                        cohortMembership.id.asc()
                )
                .offset(query.offset())
                .limit(query.size())
                .fetch();
    }

    /*
     SELECT COUNT(cm.id)
     FROM learning_service.cohort_memberships cm
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL;
     */
    @Override
    public long countActiveStudents(Long cohortId) {
        Long count = queryFactory
                .select(cohortMembership.id.count())
                .from(cohortMembership)
                .where(activeStudentOfCohort(cohortId))
                .fetchOne();

        return count == null ? 0L : count;
    }

    /*
     SELECT cm.id,
            cm.user_id
     FROM learning_service.cohort_memberships cm
     WHERE cm.id = :cohortMembershipId
       AND cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL;
     */
    @Override
    public Optional<MemberReference> findActiveStudent(
            Long cohortId,
            Long cohortMembershipId
    ) {
        MemberReference memberReference = queryFactory
                .select(Projections.constructor(
                        MemberReference.class,
                        cohortMembership.id,
                        cohortMembership.userId
                ))
                .from(cohortMembership)
                .where(
                        cohortMembership.id.eq(cohortMembershipId),
                        activeStudentOfCohort(cohortId)
                )
                .fetchOne();

        return Optional.ofNullable(memberReference);
    }

    /*
     SELECT COALESCE(SUM(sr.study_seconds), 0) AS total_study_seconds,
            COUNT(DISTINCT sr.aggregation_date) AS active_study_days,
            COUNT(sr.id) AS record_count,
            MAX(sr.end_time) AS last_studied_at
     FROM learning_service.study_records sr
     WHERE sr.cohort_membership_id = :cohortMembershipId
       AND sr.deleted_at IS NULL
       AND sr.aggregation_date BETWEEN :from AND :to;
     */
    @Override
    public PeriodSummary summarizeActiveRecords(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to
    ) {
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        NumberExpression<Long> activeStudyDays = studyRecord.aggregationDate.countDistinct();
        NumberExpression<Long> recordCount = studyRecord.id.count();
        DateTimeExpression<Instant> lastStudiedAt = studyRecord.endTime.max();
        var tuple = queryFactory
                .select(
                        totalStudySeconds,
                        activeStudyDays,
                        recordCount,
                        lastStudiedAt
                )
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(from, to)
                )
                .fetchOne();

        if (tuple == null) {
            return new PeriodSummary(0L, 0L, 0L, null);
        }
        return new PeriodSummary(
                valueOrZero(tuple.get(totalStudySeconds)),
                valueOrZero(tuple.get(activeStudyDays)),
                valueOrZero(tuple.get(recordCount)),
                tuple.get(lastStudiedAt)
        );
    }

    /*
     SELECT sr.aggregation_date,
            COALESCE(SUM(sr.study_seconds), 0) AS daily_study_seconds
     FROM learning_service.study_records sr
     WHERE sr.cohort_membership_id = :cohortMembershipId
       AND sr.deleted_at IS NULL
       AND sr.aggregation_date BETWEEN :from AND :to
     GROUP BY sr.aggregation_date
     ORDER BY sr.aggregation_date ASC;
     */
    @Override
    public List<DailyTotalResult> findMemberDailyStudySeconds(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to
    ) {
        NumberExpression<Long> dailyStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);

        return queryFactory
                .select(Projections.constructor(
                        DailyTotalResult.class,
                        studyRecord.aggregationDate,
                        dailyStudySeconds
                ))
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(from, to)
                )
                .groupBy(studyRecord.aggregationDate)
                .orderBy(studyRecord.aggregationDate.asc())
                .fetch();
    }

    /*
     SELECT sr.id,
            sr.start_time,
            sr.end_time,
            sr.study_seconds
     FROM learning_service.study_records sr
     WHERE sr.cohort_membership_id = :cohortMembershipId
       AND sr.aggregation_date = :aggregationDate
       AND sr.deleted_at IS NULL
     ORDER BY sr.start_time ASC,
              sr.id ASC;
     */
    @Override
    public List<MemberDailyRecordResult> findMemberDailyRecords(
            Long cohortMembershipId,
            LocalDate aggregationDate
    ) {
        return queryFactory
                .select(Projections.constructor(
                        MemberDailyRecordResult.class,
                        studyRecord.id,
                        studyRecord.startTime,
                        studyRecord.endTime,
                        studyRecord.studySeconds
                ))
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.aggregationDate.eq(aggregationDate),
                        studyRecord.deletedAt.isNull()
                )
                .orderBy(
                        studyRecord.startTime.asc(),
                        studyRecord.id.asc()
                )
                .fetch();
    }

    /*
     집계 Tuple의 nullable Long 값을 Application 계약의 0으로 변환한다. Java의 별도 후처리
     */
    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    /*
     ORDER BY에 사용할 첫 번째 정렬식을 요청 sort field에 따라 선택한다.
     PERIOD_STUDY_SECONDS -> period_study_seconds
     TODAY_STUDY_SECONDS  -> today_study_seconds
     ACTIVE_STUDY_DAYS    -> active_study_days
     RECORD_COUNT         -> record_count
     LAST_STUDIED_AT      -> last_studied_at
     COHORT_MEMBERSHIP_ID -> cm.id
     */
    private OrderSpecifier<?> primaryOrder(
            SortField sortField,
            SortDirection sortDirection,
            NumberExpression<Long> todayStudySeconds,
            NumberExpression<Long> periodStudySeconds,
            NumberExpression<Long> activeStudyDays,
            NumberExpression<Long> recordCount,
            DateTimeExpression<Instant> lastStudiedAt
    ) {
        return switch (sortField) {
            case PERIOD_STUDY_SECONDS -> order(periodStudySeconds, sortDirection);
            case TODAY_STUDY_SECONDS -> order(todayStudySeconds, sortDirection);
            case ACTIVE_STUDY_DAYS -> order(activeStudyDays, sortDirection);
            case RECORD_COUNT -> order(recordCount, sortDirection);
            case LAST_STUDIED_AT -> order(lastStudiedAt, sortDirection);
            case COHORT_MEMBERSHIP_ID -> order(cohortMembership.id, sortDirection);
        };
    }

    /*
     ORDER BY {expression} {ASC|DESC} NULLS LAST
     */
    private <T extends Comparable<?>> OrderSpecifier<T> order(
            ComparableExpressionBase<T> expression,
            SortDirection direction
    ) {
        return direction == SortDirection.ASC
                ? expression.asc().nullsLast()
                : expression.desc().nullsLast();
    }

    /*
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL
     */
    private BooleanExpression activeStudentOfCohort(Long cohortId) {
        return cohortMembership.cohortId.eq(cohortId)
                .and(cohortMembership.role.eq(CohortMembershipRole.STUDENT))
                .and(cohortMembership.status.eq(CohortMembershipStatus.ACTIVE))
                .and(cohortMembership.endedAt.isNull());
    }
}

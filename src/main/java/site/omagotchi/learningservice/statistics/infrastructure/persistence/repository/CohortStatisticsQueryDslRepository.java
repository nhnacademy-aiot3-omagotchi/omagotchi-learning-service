package site.omagotchi.learningservice.statistics.infrastructure.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.QCohortMembership;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.study.domain.QStudyRecord;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CohortStatisticsQueryDslRepository
        implements CohortStatisticsRepository {

    private static final QCohortMembership cohortMembership = QCohortMembership.cohortMembership;
    private static final QStudyRecord studyRecord = QStudyRecord.studyRecord;

    private final JPAQueryFactory queryFactory;

    /*
     SELECT COALESCE(SUM(sr.study_seconds), 0) AS member_study_seconds
     FROM learning_service.cohort_memberships cm
     LEFT JOIN learning_service.study_records sr
       ON sr.cohort_membership_id = cm.id
      AND sr.aggregation_date = :aggregationDate
      AND sr.deleted_at IS NULL
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL
     GROUP BY cm.id;
     */
    @Override
    public List<MemberTodayStudySeconds> findTodayStudySeconds(
            Long cohortId,
            LocalDate aggregationDate
    ) {
        NumberExpression<Long> memberStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        return queryFactory
                .select(Projections.constructor(
                        MemberTodayStudySeconds.class,
                        cohortMembership.id,
                        memberStudySeconds
                ))
                .from(cohortMembership)
                .leftJoin(studyRecord)
                .on(
                        studyRecord.cohortMembershipId.eq(cohortMembership.id),
                        studyRecord.aggregationDate.eq(aggregationDate),
                        studyRecord.deletedAt.isNull()
                )
                .where(activeStudentOfCohort(cohortId))
                .groupBy(cohortMembership.id)
                .orderBy(cohortMembership.id.asc())
                .fetch();
    }

    /*
     SELECT sr.aggregation_date,
            COALESCE(SUM(sr.study_seconds), 0) AS daily_study_seconds
     FROM learning_service.study_records sr
     JOIN learning_service.cohort_memberships cm
       ON cm.id = sr.cohort_membership_id
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL
       AND sr.deleted_at IS NULL
       AND sr.aggregation_date BETWEEN :from AND :to
     GROUP BY sr.aggregation_date
     ORDER BY sr.aggregation_date ASC;
     */
    @Override
    public List<DailyTotalResult> findDailyStudySeconds(
            Long cohortId,
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
                .join(cohortMembership)
                .on(cohortMembership.id.eq(studyRecord.cohortMembershipId))
                .where(
                        activeStudentOfCohort(cohortId),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(from, to)
                )
                .groupBy(studyRecord.aggregationDate)
                .orderBy(studyRecord.aggregationDate.asc())
                .fetch();
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

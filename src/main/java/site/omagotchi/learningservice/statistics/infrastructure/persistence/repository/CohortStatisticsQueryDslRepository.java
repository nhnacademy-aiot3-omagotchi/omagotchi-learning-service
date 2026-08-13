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
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.study.domain.QStudyRecord;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
    public TodaySummary summarizeToday(
            Long cohortId,
            LocalDate aggregationDate
    ) {
        NumberExpression<Long> memberStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        List<Long> memberTotals = queryFactory
                .select(memberStudySeconds)
                .from(cohortMembership)
                .leftJoin(studyRecord)
                .on(
                        studyRecord.cohortMembershipId.eq(cohortMembership.id),
                        studyRecord.aggregationDate.eq(aggregationDate),
                        studyRecord.deletedAt.isNull()
                )
                .where(activeStudentOfCohort(cohortId))
                .groupBy(cohortMembership.id)
                .fetch();

        long totalStudySeconds = memberTotals.stream()
                .mapToLong(Long::longValue)
                .sum();
        long participantCount = memberTotals.stream()
                .filter(studySeconds -> studySeconds > 0)
                .count();
        long activeStudentCount = memberTotals.size();

        return new TodaySummary(
                totalStudySeconds,
                activeStudentCount,
                participantCount,
                activeStudentCount - participantCount,
                durationBuckets(memberTotals)
        );
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
     DB가 반환한 수강생별 member_study_seconds를 학습 시간 구간별 인원수로 변환한다.
     모든 구간을 0명으로 먼저 생성하므로 해당 인원이 없어도 응답에서 구간이 누락되지 않는다.
     이 메서드는 Java 후처리 전용이다.
     */
    private List<DurationBucketResult> durationBuckets(List<Long> memberTotals) {
        Map<StudyDurationBucket, Long> counts = new EnumMap<>(StudyDurationBucket.class);
        Arrays.stream(StudyDurationBucket.values()).forEach(bucket -> counts.put(bucket, 0L));
        memberTotals.stream()
                .map(StudyDurationBucket::from)
                .forEach(bucket -> counts.computeIfPresent(
                        bucket,
                        (key, count) -> count + 1
                ));

        return Arrays.stream(StudyDurationBucket.values())
                .map(bucket -> new DurationBucketResult(
                        bucket.name(),
                        counts.get(bucket)
                ))
                .toList();
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

    /*
     수강생별 오늘 학습시간을 durationBuckets 후처리에 사용할 고정 구간으로 정의한다.
     이 enum은 Java 후처리 전용이다.
     */
    private enum StudyDurationBucket {
        NO_RECORD,
        UNDER_ONE_HOUR,
        ONE_TO_TWO_HOURS,
        TWO_TO_FOUR_HOURS,
        FOUR_HOURS_OR_MORE;

        /*
         0초, 1시간 미만, 1~2시간, 2~4시간, 4시간 이상의 반개방 구간으로
         */
        private static StudyDurationBucket from(long studySeconds) {
            if (studySeconds == 0) {
                return NO_RECORD;
            }
            if (studySeconds < 3_600) {
                return UNDER_ONE_HOUR;
            }
            if (studySeconds < 7_200) {
                return ONE_TO_TWO_HOURS;
            }
            if (studySeconds < 14_400) {
                return TWO_TO_FOUR_HOURS;
            }
            return FOUR_HOURS_OR_MORE;
        }
    }
}

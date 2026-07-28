package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.study.domain.entity.QStudyRecord;
import site.omagotchi.learningservice.study.domain.entity.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.projection.DailyStudySeconds;

import java.time.Instant;
import java.time.LocalDate;
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
public class StudyRecordQueryRepository {

    private static final QStudyRecord studyRecord = QStudyRecord.studyRecord;

    private final JPAQueryFactory queryFactory;

    public Optional<StudyRecord> findActiveByIdAndCohortMembershipId(
            UUID studyRecordId,
            Long cohortMembershipId
    ) {
        StudyRecord result = queryFactory
                .selectFrom(studyRecord)
                .where(
                        studyRecord.id.eq(studyRecordId),
                        activeCohortMembership(cohortMembershipId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

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
                        activeCohortMembership(cohortMembershipId),
                        studyRecord.startTime.lt(endTime),
                        studyRecord.endTime.gt(startTime),
                        excludeStudyRecord(excludedStudyRecordId)
                )
                .fetchFirst();

        return result != null;
    }

    public List<StudyRecord> findDailyRecords(
            Long cohortMembershipId,
            LocalDate aggregationDate
    ) {
        return queryFactory
                .selectFrom(studyRecord)
                .where(
                        activeCohortMembership(cohortMembershipId),
                        studyRecord.aggregationDate.eq(aggregationDate)
                )
                .orderBy(
                        studyRecord.startTime.asc(),
                        studyRecord.id.asc()
                )
                .fetch();
    }

    /**
     * 지정한 집계일 범위(시작일과 종료일 포함)의 활성 공부 시간을 날짜별로 합산한다.
     *
     * <p>실행되는 SQL과 동등한 쿼리는 다음과 같다.</p>
     * <pre>{@code
     * SELECT sr.aggregation_date,
     *        COALESCE(SUM(sr.study_seconds), 0) AS total_study_seconds
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date BETWEEN :startDate AND :endDate
     * GROUP BY sr.aggregation_date
     * ORDER BY sr.aggregation_date ASC;
     * }</pre>
     *
     * <p>기록이 없는 날짜는 결과 행에 포함되지 않는다. 전체 날짜 보정은 Application 계층에서 수행한다.</p>
     */
    public List<DailyStudySeconds> findDailyStudySeconds(
            Long cohortMembershipId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);

        return queryFactory
                .select(Projections.constructor(
                        DailyStudySeconds.class,
                        studyRecord.aggregationDate,
                        totalStudySeconds
                ))
                .from(studyRecord)
                .where(
                        activeCohortMembership(cohortMembershipId),
                        studyRecord.aggregationDate.between(startDate, endDate)
                )
                .groupBy(studyRecord.aggregationDate)
                .orderBy(studyRecord.aggregationDate.asc())
                .fetch();
    }

    private BooleanExpression activeCohortMembership(Long cohortMembershipId) {
        return studyRecord.cohortMembershipId.eq(cohortMembershipId)
                .and(studyRecord.deletedAt.isNull());
    }

    private BooleanExpression excludeStudyRecord(UUID excludedStudyRecordId) {
        return excludedStudyRecordId == null
                ? null
                : studyRecord.id.ne(excludedStudyRecordId);
    }
}

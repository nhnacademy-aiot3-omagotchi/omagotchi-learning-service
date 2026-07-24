package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static site.omagotchi.learningservice.study.infrastructure.persistence.entity.QStudyRecordEntity.studyRecordEntity;

// Spring Data가 fragment 인터페이스 이름 뒤의 Impl 접미사를 인식하여 repository에 조합
@RequiredArgsConstructor
public class StudyRecordRepositoryCustomImpl implements StudyRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /*
     * SELECT sr.*
     * FROM learning_service.study_records sr
     * WHERE sr.id = :studyRecordId
     *   AND sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL;
     */
    @Override
    public Optional<StudyRecordEntity> findActiveByIdAndCohortMembershipId(
            UUID studyRecordId,
            Long cohortMembershipId
    ) {
        StudyRecordEntity studyRecord = queryFactory
                .selectFrom(studyRecordEntity)
                .where(
                        studyRecordEntity.id.eq(studyRecordId),
                        activeCohortMembership(cohortMembershipId)
                )
                .fetchOne();

        return Optional.ofNullable(studyRecord);
    }

    /*
     * SELECT SUM(sr.study_seconds)
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date BETWEEN :fromAggregationDate AND :toAggregationDate;
     *
     * 조회 결과가 NULL이면 Java에서 0으로 변환한다.
     */
    @Override
    public long sumActiveStudySeconds(
            Long cohortMembershipId,
            LocalDate fromAggregationDate,
            LocalDate toAggregationDate
    ) {
        Long totalStudySeconds = queryFactory
                .select(studyRecordEntity.studySeconds.sumLong())
                .from(studyRecordEntity)
                .where(
                        activeCohortMembership(cohortMembershipId),
                        studyRecordEntity.aggregationDate.between(
                                fromAggregationDate,
                                toAggregationDate
                        )
                )
                .fetchOne();

        return totalStudySeconds == null ? 0L : totalStudySeconds;
    }

    /*
     * SELECT 1
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.start_time < :endTime
     *   AND sr.end_time > :startTime
     *   AND sr.id <> :excludedStudyRecordId -- 제외 식별자가 있을 때만 추가
     * LIMIT 1;
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
                .from(studyRecordEntity)
                .where(
                        activeCohortMembership(cohortMembershipId),
                        // [startTime, endTime) 반개구간이 실제 교차하는 조건
                        studyRecordEntity.startTime.lt(endTime),
                        studyRecordEntity.endTime.gt(startTime),
                        excludeStudyRecord(excludedStudyRecordId)
                )
                .fetchFirst();

        return result != null;
    }

    /**
     * 모든 복합 조회에 공통으로 적용하는 소속 및 활성 기록 조건이다.
     *
     * <pre>
     * sr.cohort_membership_id = :cohortMembershipId
     * AND sr.deleted_at IS NULL
     * </pre>
     */
    private BooleanExpression activeCohortMembership(Long cohortMembershipId) {
        return studyRecordEntity.cohortMembershipId.eq(cohortMembershipId)
                .and(studyRecordEntity.deletedAt.isNull());
    }

    /**
     * 제외 식별자가 없으면 QueryDSL이 해당 조건을 생략하도록 {@code null}을 반환한다.
     *
     * <pre>
     * sr.id &lt;&gt; :excludedStudyRecordId
     * </pre>
     */
    private BooleanExpression excludeStudyRecord(UUID excludedStudyRecordId) {
        return excludedStudyRecordId == null
                ? null
                : studyRecordEntity.id.ne(excludedStudyRecordId);
    }
}

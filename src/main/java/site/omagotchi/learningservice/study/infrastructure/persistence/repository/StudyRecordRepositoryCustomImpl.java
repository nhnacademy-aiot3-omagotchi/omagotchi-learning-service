package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static site.omagotchi.learningservice.study.infrastructure.persistence.entity.QStudyRecordEntity.studyRecordEntity;

/**
 * {@link StudyRecordRepositoryCustom}의 QueryDSL 구현체다.
 *
 * <p>Spring Data가 fragment 인터페이스 이름 뒤의 {@code Impl} 접미사를 인식하여
 * 이 구현을 {@link StudyRecordRepository}에 조합한다.</p>
 */
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
     * 데이터 조회 SQL(Pageable이 paged인 경우 LIMIT/OFFSET 적용):
     * SELECT sr.*
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date BETWEEN :fromAggregationDate AND :toAggregationDate
     * ORDER BY sr.aggregation_date, sr.start_time, sr.id
     * LIMIT :pageSize OFFSET :offset;
     *
     * 전체 개수 조회 SQL:
     * SELECT COUNT(sr.id)
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.aggregation_date BETWEEN :fromAggregationDate AND :toAggregationDate;
     */
    @Override
    public Page<StudyRecordEntity> findActiveRecords(
            Long cohortMembershipId,
            LocalDate fromAggregationDate,
            LocalDate toAggregationDate,
            Pageable pageable
    ) {
        BooleanExpression searchCondition = activeCohortMembership(cohortMembershipId)
                .and(studyRecordEntity.aggregationDate.between(fromAggregationDate, toAggregationDate));

        JPAQuery<StudyRecordEntity> contentQuery = queryFactory
                .selectFrom(studyRecordEntity)
                .where(searchCondition)
                // 페이지 사이에서 순서가 바뀌지 않도록 고유 식별자를 마지막 정렬 기준으로 사용한다.
                .orderBy(
                        studyRecordEntity.aggregationDate.asc(),
                        studyRecordEntity.startTime.asc(),
                        studyRecordEntity.id.asc()
                );

        if (pageable.isPaged()) {
            contentQuery
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize());
        }

        List<StudyRecordEntity> content = contentQuery.fetch();
        Long total = queryFactory
                .select(studyRecordEntity.count())
                .from(studyRecordEntity)
                .where(searchCondition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    /*
     * 대응 SQL:
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
     * 대응 SQL:
     * SELECT 1
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.start_time < :endTime
     *   AND sr.end_time > :startTime
     * LIMIT 1;
     */
    @Override
    public boolean existsActiveOverlap(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime
    ) {
        return existsActiveOverlap(
                cohortMembershipId,
                startTime,
                endTime,
                null
        );
    }

    /*
     * 대응 SQL:
     * SELECT 1
     * FROM learning_service.study_records sr
     * WHERE sr.cohort_membership_id = :cohortMembershipId
     *   AND sr.deleted_at IS NULL
     *   AND sr.start_time < :endTime
     *   AND sr.end_time > :startTime
     *   AND sr.id <> :excludedStudyRecordId
     * LIMIT 1;
     */
    @Override
    public boolean existsActiveOverlapExcluding(
            Long cohortMembershipId,
            UUID excludedStudyRecordId,
            Instant startTime,
            Instant endTime
    ) {
        return existsActiveOverlap(
                cohortMembershipId,
                startTime,
                endTime,
                excludedStudyRecordId
        );
    }

    /*
     * 공통 겹침 조회 SQL을 생성한다.
     * excludedStudyRecordId가 NULL이면 sr.id <> :excludedStudyRecordId 조건을 생성하지 않는다.
     */
    private boolean existsActiveOverlap(
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
                        // [startTime, endTime) 반개구간이 실제로 교차하는 조건이다.
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

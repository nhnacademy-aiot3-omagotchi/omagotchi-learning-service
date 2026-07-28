package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import site.omagotchi.learningservice.study.domain.entity.StudyRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 공부 기록의 복합 조회를 QueryDSL로 제공하는 Spring Data Repository fragment 계약이다.
 *
 * <p>이 인터페이스에서 활성 기록은 {@code deletedAt IS NULL}인 기록을 의미한다.</p>
 */
public interface StudyRecordRepositoryCustom {

    /**
     * 지정한 소속이 소유한 활성 공부 기록을 식별자로 조회한다.
     *
     * @param studyRecordId      공부 기록 식별자
     * @param cohortMembershipId 기수 소속 식별자
     * @return 조건을 만족하는 활성 기록, 없으면 {@link Optional#empty()}
     */
    Optional<StudyRecord> findActiveByIdAndCohortMembershipId(
            UUID studyRecordId,
            Long cohortMembershipId
    );

    /**
     * 지정한 소속과 집계일 범위에 포함된 활성 기록의 공부 시간을 합산한다.
     *
     * @param cohortMembershipId  기수 소속 식별자
     * @param fromAggregationDate 합산 시작 집계일
     * @param toAggregationDate   합산 종료 집계일
     * @return 공부 시간 합계(초), 조회 결과가 없으면 {@code 0}
     */
    long sumActiveStudySeconds(
            Long cohortMembershipId,
            LocalDate fromAggregationDate,
            LocalDate toAggregationDate
    );

    /**
     * 선택적으로 특정 기록을 제외하고 요청 구간과 겹치는 활성 기록이 있는지 확인한다.
     *
     * @param cohortMembershipId    기수 소속 식별자
     * @param startTime             요청 구간의 포함 시작 시각
     * @param endTime               요청 구간의 미포함 종료 시각
     * @param excludedStudyRecordId 겹침 검사에서 제외할 공부 기록 식별자, 제외하지 않으면 {@code null}
     * @return 제외 대상을 뺀 나머지 활성 기록과 겹치면 {@code true}
     */
    boolean existsActiveOverlap(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            UUID excludedStudyRecordId
    );
}

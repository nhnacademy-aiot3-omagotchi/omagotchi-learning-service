package site.omagotchi.learningservice.gamification.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

import java.time.LocalDate;
import java.util.UUID;

public interface StudyProgressionRepository extends Repository<UserCharacter, Long> {

    @Query(
            value = """
                    SELECT COALESCE(SUM(sr.study_seconds), 0)
                    FROM learning_service.study_records sr
                    JOIN learning_service.cohort_memberships cm
                      ON cm.id = sr.cohort_membership_id
                    WHERE cm.user_id = :userId
                      AND cm.cohort_id = :cohortId
                      AND cm.status = 'ACTIVE'
                      AND sr.deleted_at IS NULL
                      AND sr.aggregation_date = :aggregationDate
                    """,
            nativeQuery = true
    )
    long getDailyStudySeconds(
            @Param("userId") UUID userId,
            @Param("cohortId") Long cohortId,
            @Param("aggregationDate") LocalDate aggregationDate
    );

    /**
     * 최근 7 등원일의 하루 공부시간 평균(초). 예측 실패 시의 B2 규칙 폴백에 사용한다.
     *
     * <p>등원일은 출결 상태가 아니라 삭제되지 않은 확정 StudyRecord가 있는 날로 판단한다
     * (ADR prediction/0002). 등원일이 하나도 없으면 0을 돌려준다.
     */
    @Query(
            value = """
                    SELECT COALESCE(AVG(daily.total_seconds), 0)
                    FROM (
                        SELECT sr.aggregation_date, SUM(sr.study_seconds) AS total_seconds
                        FROM learning_service.study_records sr
                        JOIN learning_service.cohort_memberships cm
                          ON cm.id = sr.cohort_membership_id
                        WHERE cm.user_id = :userId
                          AND cm.cohort_id = :cohortId
                          AND cm.status = 'ACTIVE'
                          AND sr.deleted_at IS NULL
                          AND sr.aggregation_date < :aggregationDate
                        GROUP BY sr.aggregation_date
                        ORDER BY sr.aggregation_date DESC
                        LIMIT 7
                    ) daily
                    """,
            nativeQuery = true
    )
    double getRecentAttendedAverageStudySeconds(
            @Param("userId") UUID userId,
            @Param("cohortId") Long cohortId,
            @Param("aggregationDate") LocalDate aggregationDate
    );

    /**
     * 해당 집계일 이전에 삭제되지 않은 확정 StudyRecord가 하나라도 있는지. 콜드스타트 판정에 쓴다.
     *
     * <p>기록이 하나도 없으면 모델 입력이 전부 0이라 예측이 아니라 외삽이 나온다.
     * 그런 사용자는 모델을 부르지 않고 기본 목표로 보낸다(ADR prediction/0002 §3.2).
     */
    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM learning_service.study_records sr
                        JOIN learning_service.cohort_memberships cm
                          ON cm.id = sr.cohort_membership_id
                        WHERE cm.user_id = :userId
                          AND cm.cohort_id = :cohortId
                          AND cm.status = 'ACTIVE'
                          AND sr.deleted_at IS NULL
                          AND sr.aggregation_date < :aggregationDate
                    )
                    """,
            nativeQuery = true
    )
    boolean existsStudyRecordBefore(
            @Param("userId") UUID userId,
            @Param("cohortId") Long cohortId,
            @Param("aggregationDate") LocalDate aggregationDate
    );
}

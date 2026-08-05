package site.omagotchi.learningservice.ranking.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface StudyTimeRankingRepository extends Repository<RankingSnapshot, Long> {

    @Query(
            value = """
                    SELECT cm.user_id AS "userId",
                           COALESCE(SUM(sr.study_seconds), 0) AS "studySeconds"
                    FROM learning_service.study_records sr
                    JOIN learning_service.cohort_memberships cm
                      ON cm.id = sr.cohort_membership_id
                    WHERE cm.cohort_id = :cohortId
                      AND cm.status = 'ACTIVE'
                      AND sr.deleted_at IS NULL
                      AND sr.aggregation_date BETWEEN :startDate AND :endDate
                    GROUP BY cm.user_id
                    HAVING COALESCE(SUM(sr.study_seconds), 0) > 0
                    """,
            nativeQuery = true
    )
    List<StudyTimeRankingRow> findStudySeconds(
            @Param("cohortId") Long cohortId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

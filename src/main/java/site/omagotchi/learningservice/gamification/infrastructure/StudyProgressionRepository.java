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
}

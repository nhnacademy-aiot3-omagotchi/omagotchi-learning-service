package site.omagotchi.learningservice.ranking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;

import java.time.LocalDate;
import java.util.Optional;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    Optional<RankingSnapshot> findByCohortIdAndPeriodAndBaseDate(
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO learning_service.ranking_snapshots (
                        cohort_id,
                        period,
                        base_date,
                        range_start_date,
                        range_end_date
                    )
                    VALUES (
                        :cohortId,
                        :period,
                        :baseDate,
                        :rangeStartDate,
                        :rangeEndDate
                    )
                    ON CONFLICT (cohort_id, period, base_date) DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("cohortId") Long cohortId,
            @Param("period") String period,
            @Param("baseDate") LocalDate baseDate,
            @Param("rangeStartDate") LocalDate rangeStartDate,
            @Param("rangeEndDate") LocalDate rangeEndDate
    );
}

package site.omagotchi.learningservice.ranking.application.port;

import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingSnapshotPort {

    int insertIfAbsent(
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate,
            LocalDate rangeStartDate,
            LocalDate rangeEndDate
    );

    Optional<RankingSnapshot> findByCohortIdAndPeriodAndBaseDate(
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate
    );

    void saveEntries(List<RankingSnapshotEntry> entries);

    List<RankingSnapshotEntry> findEntries(Long snapshotId);

    Optional<RankingSnapshotEntry> findEntry(Long snapshotId, Long userCharacterId);
}

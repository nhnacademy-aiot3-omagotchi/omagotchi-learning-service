package site.omagotchi.learningservice.ranking.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.ranking.application.port.RankingSnapshotPort;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotEntryRepository;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RankingSnapshotAdapter implements RankingSnapshotPort {

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final RankingSnapshotEntryRepository rankingSnapshotEntryRepository;

    @Override
    public int insertIfAbsent(
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate,
            LocalDate rangeStartDate,
            LocalDate rangeEndDate
    ) {
        return rankingSnapshotRepository.insertIfAbsent(
                cohortId,
                period.name(),
                baseDate,
                rangeStartDate,
                rangeEndDate
        );
    }

    @Override
    public Optional<RankingSnapshot> findByCohortIdAndPeriodAndBaseDate(
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate
    ) {
        return rankingSnapshotRepository.findByCohortIdAndPeriodAndBaseDate(cohortId, period, baseDate);
    }

    @Override
    public void saveEntries(List<RankingSnapshotEntry> entries) {
        rankingSnapshotEntryRepository.saveAll(entries);
    }

    @Override
    public List<RankingSnapshotEntry> findEntries(Long snapshotId) {
        return rankingSnapshotEntryRepository.findBySnapshotIdOrderByRankAscStudySecondsDescUserCharacterIdAsc(snapshotId);
    }

    @Override
    public Optional<RankingSnapshotEntry> findEntry(Long snapshotId, Long userCharacterId) {
        return rankingSnapshotEntryRepository.findBySnapshotIdAndUserCharacterId(snapshotId, userCharacterId);
    }
}

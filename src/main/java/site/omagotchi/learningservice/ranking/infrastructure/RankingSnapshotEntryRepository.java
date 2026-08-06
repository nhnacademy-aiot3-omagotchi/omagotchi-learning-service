package site.omagotchi.learningservice.ranking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;

import java.util.List;
import java.util.Optional;

public interface RankingSnapshotEntryRepository extends JpaRepository<RankingSnapshotEntry, Long> {

    List<RankingSnapshotEntry> findBySnapshotIdOrderByRankAscStudySecondsDescUserCharacterIdAsc(Long snapshotId);

    Optional<RankingSnapshotEntry> findBySnapshotIdAndUserCharacterId(Long snapshotId, Long userCharacterId);
}

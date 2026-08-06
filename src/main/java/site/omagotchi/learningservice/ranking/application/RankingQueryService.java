package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.ranking.application.port.RankingSnapshotPort;
import site.omagotchi.learningservice.ranking.application.result.RankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingResult;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    private final RankingSnapshotService rankingSnapshotService;
    private final RankingSnapshotPort rankingSnapshotPort;
    private final CharacterGrowthService characterGrowthService;
    private final DateTimeProvider dateTimeProvider;

    public StudyRankingResult getStudyRanking(
            UUID userId,
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate
    ) {
        LocalDate targetDate = baseDate == null ? dateTimeProvider.currentAggregationDate() : baseDate;
        RankingSnapshot snapshot = rankingSnapshotService.getOrCreate(cohortId, period, targetDate);
        List<RankingEntryResult> top10 = rankingSnapshotPort
                .findEntries(snapshot.getId())
                .stream()
                .limit(10)
                .map(RankingEntryResult::from)
                .toList();
        RankingEntryResult myRank = characterGrowthService
                .findRepresentativeCharacter(userId)
                .flatMap(character -> rankingSnapshotPort.findEntry(
                        snapshot.getId(),
                        character.userCharacterId()
                ))
                .map(RankingEntryResult::from)
                .orElse(null);

        return StudyRankingResult.of(snapshot, top10, myRank);
    }
}

package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.ranking.application.result.RankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingResult;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotEntryRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    private final RankingSnapshotService rankingSnapshotService;
    private final RankingSnapshotEntryRepository rankingSnapshotEntryRepository;
    private final UserCharacterRepository userCharacterRepository;
    private final DateTimeProvider dateTimeProvider;

    public StudyRankingResult getStudyRanking(
            UUID userId,
            Long cohortId,
            RankingPeriod period,
            LocalDate baseDate
    ) {
        LocalDate targetDate = baseDate == null ? dateTimeProvider.currentAggregationDate() : baseDate;
        RankingSnapshot snapshot = rankingSnapshotService.getOrCreate(cohortId, period, targetDate);
        List<RankingEntryResult> top10 = rankingSnapshotEntryRepository
                .findBySnapshotIdOrderByRankAscStudySecondsDescUserCharacterIdAsc(snapshot.getId())
                .stream()
                .limit(10)
                .map(RankingEntryResult::from)
                .toList();
        RankingEntryResult myRank = userCharacterRepository
                .findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(userId)
                .flatMap(character -> rankingSnapshotEntryRepository.findBySnapshotIdAndUserCharacterId(
                        snapshot.getId(),
                        character.getId()
                ))
                .map(RankingEntryResult::from)
                .orElse(null);

        return StudyRankingResult.of(snapshot, top10, myRank);
    }
}

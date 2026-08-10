package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.ranking.application.port.RankingSnapshotPort;
import site.omagotchi.learningservice.ranking.application.port.StudyTimeRankingQueryPort;
import site.omagotchi.learningservice.ranking.application.result.StudyTimeRankingResult;
import site.omagotchi.learningservice.ranking.domain.RankingCalculator;
import site.omagotchi.learningservice.ranking.domain.RankingCandidate;
import site.omagotchi.learningservice.ranking.domain.RankingDateRange;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingSnapshotService {

    private final RankingSnapshotPort rankingSnapshotPort;
    private final StudyTimeRankingQueryPort studyTimeRankingQueryPort;
    private final CharacterGrowthService characterGrowthService;

    @Transactional
    public RankingSnapshot getOrCreate(Long cohortId, RankingPeriod period, LocalDate baseDate) {
        RankingDateRange range = RankingDateRange.from(period, baseDate);
        // 같은 기간 snapshot 동시 생성은 DB upsert 기준으로 한 번만 통과시킴
        int inserted = rankingSnapshotPort.insertIfAbsent(
                cohortId,
                period,
                baseDate,
                range.startDate(),
                range.endDate()
        );
        RankingSnapshot snapshot = rankingSnapshotPort.findByCohortIdAndPeriodAndBaseDate(cohortId, period, baseDate)
                .orElseThrow(() -> new IllegalStateException("ranking snapshot insert failed"));
        if (inserted > 0) {
            List<RankingSnapshotEntry> entries = rankingEntries(snapshot.getId(), cohortId, range);
            rankingSnapshotPort.saveEntries(entries);
        }
        return snapshot;
    }

    private List<RankingSnapshotEntry> rankingEntries(Long snapshotId, Long cohortId, RankingDateRange range) {
        List<StudyTimeRankingResult> studyRows = studyTimeRankingQueryPort.findStudySeconds(
                cohortId,
                range.startDate(),
                range.endDate()
        );
        Map<UUID, StudyTimeRankingResult> studyRowByUserId = studyRows.stream()
                .collect(Collectors.toMap(StudyTimeRankingResult::userId, Function.identity()));
        Map<UUID, RepresentativeCharacterResult> characterByUserId = characterGrowthService
                .findRepresentativeCharacters(studyRowByUserId.keySet())
                .stream()
                .collect(Collectors.toMap(RepresentativeCharacterResult::userId, Function.identity()));

        List<RankingCandidate> candidates = studyRows.stream()
                .filter(row -> characterByUserId.containsKey(row.userId()))
                .map(row -> {
                    RepresentativeCharacterResult character = characterByUserId.get(row.userId());
                    return new RankingCandidate(
                            row.userId(),
                            character.userCharacterId(),
                            character.displayName(),
                            row.studySeconds()
                    );
                })
                .toList();

        return RankingCalculator.rank(candidates).stream()
                .map(entry -> RankingSnapshotEntry.from(snapshotId, entry))
                .toList();
    }
}

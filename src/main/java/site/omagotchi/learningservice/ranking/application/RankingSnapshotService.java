package site.omagotchi.learningservice.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.ranking.domain.RankingCalculator;
import site.omagotchi.learningservice.ranking.domain.RankingCandidate;
import site.omagotchi.learningservice.ranking.domain.RankingDateRange;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotEntryRepository;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotRepository;
import site.omagotchi.learningservice.ranking.infrastructure.StudyTimeRankingRow;
import site.omagotchi.learningservice.ranking.infrastructure.StudyTimeRankingRepository;

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

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final RankingSnapshotEntryRepository rankingSnapshotEntryRepository;
    private final StudyTimeRankingRepository studyTimeRankingRepository;
    private final UserCharacterRepository userCharacterRepository;

    @Transactional
    public RankingSnapshot getOrCreate(Long cohortId, RankingPeriod period, LocalDate baseDate) {
        RankingDateRange range = RankingDateRange.from(period, baseDate);
        // 같은 기간 snapshot 동시 생성은 DB upsert 기준으로 한 번만 통과시킴
        int inserted = rankingSnapshotRepository.insertIfAbsent(
                cohortId,
                period.name(),
                baseDate,
                range.startDate(),
                range.endDate()
        );
        RankingSnapshot snapshot = rankingSnapshotRepository.findByCohortIdAndPeriodAndBaseDate(cohortId, period, baseDate)
                .orElseThrow(() -> new IllegalStateException("ranking snapshot insert failed"));
        if (inserted > 0) {
            List<RankingSnapshotEntry> entries = rankingEntries(snapshot.getId(), cohortId, range);
            rankingSnapshotEntryRepository.saveAll(entries);
        }
        return snapshot;
    }

    private List<RankingSnapshotEntry> rankingEntries(Long snapshotId, Long cohortId, RankingDateRange range) {
        List<StudyTimeRankingRow> studyRows = studyTimeRankingRepository.findStudySeconds(
                cohortId,
                range.startDate(),
                range.endDate()
        );
        Map<UUID, StudyTimeRankingRow> studyRowByUserId = studyRows.stream()
                .collect(Collectors.toMap(StudyTimeRankingRow::getUserId, Function.identity()));
        Map<UUID, UserCharacter> characterByUserId = userCharacterRepository
                .findByUserIdInAndRepresentativeTrue(studyRowByUserId.keySet())
                .stream()
                .collect(Collectors.toMap(UserCharacter::getUserId, Function.identity()));

        List<RankingCandidate> candidates = studyRows.stream()
                .filter(row -> characterByUserId.containsKey(row.getUserId()))
                .map(row -> {
                    UserCharacter character = characterByUserId.get(row.getUserId());
                    return new RankingCandidate(
                            row.getUserId(),
                            character.getId(),
                            character.displayName(),
                            row.getStudySeconds()
                    );
                })
                .toList();

        return RankingCalculator.rank(candidates).stream()
                .map(entry -> RankingSnapshotEntry.from(snapshotId, entry))
                .toList();
    }
}

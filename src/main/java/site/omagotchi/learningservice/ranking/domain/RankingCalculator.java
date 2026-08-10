package site.omagotchi.learningservice.ranking.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RankingCalculator {

    public static List<RankingRankedEntry> rank(List<RankingCandidate> candidates) {
        List<RankingCandidate> sortedCandidates = candidates.stream()
                .sorted(Comparator.comparingLong(RankingCandidate::studySeconds).reversed()
                        .thenComparing(RankingCandidate::userCharacterId))
                .toList();

        List<RankingRankedEntry> entries = new ArrayList<>();
        long previousStudySeconds = -1;
        int previousRank = 0;
        for (int index = 0; index < sortedCandidates.size(); index++) {
            RankingCandidate candidate = sortedCandidates.get(index);
            int rank = candidate.studySeconds() == previousStudySeconds ? previousRank : index + 1;
            // 동점자는 같은 순위로 묶고, 다음 순위는 건너뜀
            entries.add(new RankingRankedEntry(
                    rank,
                    candidate.userId(),
                    candidate.userCharacterId(),
                    candidate.displayName(),
                    candidate.studySeconds()
            ));
            previousStudySeconds = candidate.studySeconds();
            previousRank = rank;
        }
        return entries;
    }
}

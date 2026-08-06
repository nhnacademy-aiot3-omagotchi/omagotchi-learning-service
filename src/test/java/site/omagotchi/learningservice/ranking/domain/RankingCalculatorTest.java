package site.omagotchi.learningservice.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("랭킹 계산")
class RankingCalculatorTest {

    @Test
    @DisplayName("동점자는 공동 순위로 묶고 다음 순위는 건너뛴다")
    void ranksTiesWithCompetitionRanking() {
        List<RankingRankedEntry> entries = RankingCalculator.rank(List.of(
                candidate(1L, 28_800),
                candidate(2L, 28_800),
                candidate(3L, 21_600)
        ));

        assertEquals(List.of(1, 1, 3), entries.stream()
                .map(RankingRankedEntry::rank)
                .toList());
    }

    private RankingCandidate candidate(Long characterId, long studySeconds) {
        return new RankingCandidate(UUID.randomUUID(), characterId, "캐릭터" + characterId, studySeconds);
    }
}

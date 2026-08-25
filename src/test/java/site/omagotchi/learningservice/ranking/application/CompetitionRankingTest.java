package site.omagotchi.learningservice.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("공동 순위 계산")
class CompetitionRankingTest {

    @Test
    @DisplayName("0점 제외와 동점 순위 정상 처리")
    void ranksPositiveScoresWithCompetitionRanking() {
        List<CompetitionRanking.Ranked<Score>> result = CompetitionRanking.rank(
                List.of(
                        new Score(4L, 0L),
                        new Score(3L, 3_600L),
                        new Score(1L, 7_200L),
                        new Score(2L, 3_600L)
                ),
                Score::seconds,
                Comparator.comparing(Score::id)
        );

        assertEquals(
                List.of("1:1", "2:2", "2:3"),
                result.stream()
                        .map(ranked -> ranked.rank() + ":" + ranked.value().id())
                        .toList()
        );
    }

    private record Score(Long id, long seconds) {
    }
}

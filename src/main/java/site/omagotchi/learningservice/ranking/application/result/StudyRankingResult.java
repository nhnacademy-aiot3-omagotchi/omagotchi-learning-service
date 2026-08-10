package site.omagotchi.learningservice.ranking.application.result;

import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudyRankingResult(
        RankingPeriod period,
        LocalDate baseDate,
        LocalDate rangeStartDate,
        LocalDate rangeEndDate,
        Instant generatedAt,
        List<RankingEntryResult> top10,
        RankingEntryResult myRank
) {

    public StudyRankingResult {
        top10 = List.copyOf(top10);
    }

    public static StudyRankingResult of(
            RankingSnapshot snapshot,
            List<RankingEntryResult> top10,
            RankingEntryResult myRank
    ) {
        return new StudyRankingResult(
                snapshot.getPeriod(),
                snapshot.getBaseDate(),
                snapshot.getRangeStartDate(),
                snapshot.getRangeEndDate(),
                snapshot.getGeneratedAt(),
                top10,
                myRank
        );
    }
}

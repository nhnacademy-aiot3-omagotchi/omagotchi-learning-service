package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.StudyRankingResult;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudyRankingResponse(
        RankingPeriod period,
        LocalDate baseDate,
        LocalDate rangeStartDate,
        LocalDate rangeEndDate,
        Instant generatedAt,
        List<RankingEntryResponse> top10,
        RankingEntryResponse myRank
) {

    public static StudyRankingResponse from(StudyRankingResult result) {
        return new StudyRankingResponse(
                result.period(),
                result.baseDate(),
                result.rangeStartDate(),
                result.rangeEndDate(),
                result.generatedAt(),
                result.top10().stream()
                        .map(RankingEntryResponse::from)
                        .toList(),
                result.myRank() == null ? null : RankingEntryResponse.from(result.myRank())
        );
    }
}

package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;

import java.util.List;

public record StudyRankingBoardResponse(
        long rankedMemberCount,
        int returnedEntryCount,
        List<StudyRankingEntryResponse> entries
) {

    public static StudyRankingBoardResponse from(StudyRankingBoardResult result) {
        List<StudyRankingEntryResponse> entries = result.entries().stream()
                .map(StudyRankingEntryResponse::from)
                .toList();
        return new StudyRankingBoardResponse(
                result.rankedMemberCount(),
                entries.size(),
                entries
        );
    }
}

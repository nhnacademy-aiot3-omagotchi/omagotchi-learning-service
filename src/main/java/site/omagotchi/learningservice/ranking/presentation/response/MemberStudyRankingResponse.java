package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;

import java.util.List;

public record MemberStudyRankingResponse(
        long rankedMemberCount,
        int returnedEntryCount,
        List<StudyRankingEntryResponse> entries,
        PersonalStudyRankingResponse myRanking
) {

    public static MemberStudyRankingResponse from(MemberStudyRankingViewResult result) {
        StudyRankingBoardResult board = result.board();
        List<StudyRankingEntryResponse> entries = board.entries().stream()
                .map(StudyRankingEntryResponse::from)
                .toList();
        return new MemberStudyRankingResponse(
                board.rankedMemberCount(),
                entries.size(),
                entries,
                PersonalStudyRankingResponse.from(result.mine())
        );
    }
}

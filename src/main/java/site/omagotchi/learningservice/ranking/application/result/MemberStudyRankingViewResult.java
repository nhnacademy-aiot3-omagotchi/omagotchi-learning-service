package site.omagotchi.learningservice.ranking.application.result;

public record MemberStudyRankingViewResult(
        StudyRankingBoardResult board,
        MyStudyRankingResult mine
) {
}

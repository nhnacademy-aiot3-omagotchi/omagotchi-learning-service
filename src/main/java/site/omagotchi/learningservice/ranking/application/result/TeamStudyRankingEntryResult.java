package site.omagotchi.learningservice.ranking.application.result;

public record TeamStudyRankingEntryResult(
        long rank,
        Long teamId,
        String teamName,
        long studySeconds
) {
}

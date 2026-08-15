package site.omagotchi.learningservice.ranking.application.result;

public record StudyRankingEntryResult(
        long rank,
        String displayName,
        long studySeconds
) {
}

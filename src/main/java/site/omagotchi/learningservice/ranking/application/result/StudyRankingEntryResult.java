package site.omagotchi.learningservice.ranking.application.result;

public record StudyRankingEntryResult(
        long rank,
        String displayName,
        long studySeconds,
        boolean timerRunning
) {

    public StudyRankingEntryResult(long rank, String displayName, long studySeconds) {
        this(rank, displayName, studySeconds, false);
    }
}

package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TodayResult(
        LocalDate aggregationDate,
        Instant calculatedAt,
        long totalStudySeconds,
        long activeStudentCount,
        long participantCount,
        long noRecordStudentCount,
        long runningTimerCount,
        long averageParticipantStudySeconds,
        List<DurationBucketResult> durationBuckets
) {

    public TodayResult {
        durationBuckets = List.copyOf(durationBuckets);
    }

}

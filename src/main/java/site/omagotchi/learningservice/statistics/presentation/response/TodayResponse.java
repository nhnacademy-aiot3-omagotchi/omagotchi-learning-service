package site.omagotchi.learningservice.statistics.presentation.response;

import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TodayResponse(
        LocalDate aggregationDate,
        Instant calculatedAt,
        long totalStudySeconds,
        long activeStudentCount,
        long participantCount,
        long noRecordStudentCount,
        long runningTimerCount,
        long averageParticipantStudySeconds,
        List<DurationBucket> durationBuckets
) {

    public TodayResponse {
        durationBuckets = List.copyOf(durationBuckets);
    }

    public static TodayResponse from(TodayResult result) {
        return new TodayResponse(
                result.aggregationDate(),
                result.calculatedAt(),
                result.totalStudySeconds(),
                result.activeStudentCount(),
                result.participantCount(),
                result.noRecordStudentCount(),
                result.runningTimerCount(),
                result.averageParticipantStudySeconds(),
                result.durationBuckets().stream()
                        .map(DurationBucket::from)
                        .toList()
        );
    }

    public record DurationBucket(
            String code,
            long memberCount
    ) {

        private static DurationBucket from(
                DurationBucketResult result
        ) {
            return new DurationBucket(result.code(), result.memberCount());
        }
    }
}

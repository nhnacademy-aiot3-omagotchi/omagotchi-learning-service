package site.omagotchi.learningservice.statistics.application.port;

import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;

import java.time.LocalDate;
import java.util.List;

public interface CohortStatisticsRepository {

    TodaySummary summarizeToday(
            Long cohortId,
            LocalDate aggregationDate
    );

    List<DailyTotalResult> findDailyStudySeconds(
            Long cohortId,
            LocalDate from,
            LocalDate to
    );

    record TodaySummary(
            long totalStudySeconds,
            long activeStudentCount,
            long participantCount,
            long noRecordStudentCount,
            List<DurationBucketResult> durationBuckets
    ) {

        public TodaySummary {
            durationBuckets = List.copyOf(durationBuckets);
        }
    }
}

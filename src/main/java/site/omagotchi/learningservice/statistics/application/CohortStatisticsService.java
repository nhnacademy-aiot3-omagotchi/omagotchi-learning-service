package site.omagotchi.learningservice.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.TodaySummary;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery.DateRange;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortStatisticsService {

    private final CohortAccessService cohortAccessService;
    private final CohortStatisticsRepository cohortStatisticsRepository;
    private final Clock clock;

    public TodayResult getToday(
            UUID managerUserId,
            Long cohortId
    ) {
        cohortAccessService.requireManager(cohortId, managerUserId);

        Instant calculatedAt = clock.instant();
        LocalDate aggregationDate = StudyTimePolicy.aggregationDate(calculatedAt);
        TodaySummary summary = cohortStatisticsRepository.summarizeToday(
                cohortId,
                aggregationDate
        );
        long averageParticipantStudySeconds = summary.participantCount() == 0
                ? 0L
                : summary.totalStudySeconds() / summary.participantCount();

        // TODO: 실시간 통계 정책 확정 후 실행 중 TimerRun의 경과 시간을 이 확정 기록 조립 단계에서 합산한다.
        return new TodayResult(
                aggregationDate,
                calculatedAt,
                summary.totalStudySeconds(),
                summary.activeStudentCount(),
                summary.participantCount(),
                summary.noRecordStudentCount(),
                averageParticipantStudySeconds,
                summary.durationBuckets()
        );
    }

    public TrendResult getTrend(
            UUID managerUserId,
            Long cohortId,
            String requestedWindow
    ) {
        cohortAccessService.requireManager(cohortId, managerUserId);

        WindowQuery window = WindowQuery.parse(requestedWindow);
        Instant calculatedAt = clock.instant();
        DateRange dateRange = window.resolveAt(calculatedAt);
        List<DailyTotalResult> dailyTotals = fillDailyTotals(
                dateRange,
                cohortStatisticsRepository.findDailyStudySeconds(
                        cohortId,
                        dateRange.from(),
                        dateRange.to()
                )
        );
        long totalStudySeconds = dailyTotals.stream()
                .mapToLong(DailyTotalResult::studySeconds)
                .sum();

        return new TrendResult(
                window.value(),
                dateRange.from(),
                dateRange.to(),
                calculatedAt,
                totalStudySeconds,
                totalStudySeconds / window.days(),
                dailyTotals
        );
    }

    private List<DailyTotalResult> fillDailyTotals(
            DateRange dateRange,
            List<DailyTotalResult> sparseDailyTotals
    ) {
        Map<LocalDate, Long> totalsByDate = sparseDailyTotals.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DailyTotalResult::aggregationDate,
                        DailyTotalResult::studySeconds
                ));

        return IntStream.range(0, dateRange.window().days())
                .mapToObj(dateRange.from()::plusDays)
                .map(aggregationDate -> new DailyTotalResult(
                        aggregationDate,
                        totalsByDate.getOrDefault(aggregationDate, 0L)
                ))
                .toList();
    }
}

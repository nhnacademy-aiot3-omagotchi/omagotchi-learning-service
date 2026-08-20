package site.omagotchi.learningservice.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.TodaySummary;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery.DateRange;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;

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
        // 관리자 권한 검증
        cohortAccessService.requireManager(cohortId, managerUserId);

        // 요청 기준 시각과 현재 집계일 계산
        Instant calculatedAt = clock.instant();
        LocalDate aggregationDate = AggregationDateTime.aggregationDate(calculatedAt);

        // 오늘 확정 학습 통계 DB 조회
        TodaySummary summary = cohortStatisticsRepository.summarizeToday(
                cohortId,
                aggregationDate
        );

        // 참여자 평균 계산 및 응답 결과 조립
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
        // 관리자 권한 검증
        cohortAccessService.requireManager(cohortId, managerUserId);

        // 조회 window 검증과 요청 기준 기간 계산
        WindowQuery window = WindowQuery.parse(requestedWindow);
        Instant calculatedAt = clock.instant();
        DateRange dateRange = window.resolveAt(calculatedAt);

        // 기간별 확정 학습 통계 DB 조회 및 빈 날짜 보정
        List<DailyTotalResult> dailyTotals = fillDailyTotals(
                dateRange,
                cohortStatisticsRepository.findDailyStudySeconds(
                        cohortId,
                        dateRange.from(),
                        dateRange.to()
                )
        );

        // 기간 합계·평균 계산 및 응답 결과 조립
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
        // DB가 반환한 날짜별 합계를 빠르게 조회할 수 있도록 변환
        Map<LocalDate, Long> totalsByDate = sparseDailyTotals.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DailyTotalResult::aggregationDate,
                        DailyTotalResult::studySeconds
                ));

        // 요청 window의 모든 날짜를 오름차순으로 채워 반환
        return IntStream.range(0, dateRange.window().days())
                .mapToObj(dateRange.from()::plusDays)
                .map(aggregationDate -> new DailyTotalResult(
                        aggregationDate,
                        totalsByDate.getOrDefault(aggregationDate, 0L)
                ))
                .toList();
    }
}

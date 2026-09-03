package site.omagotchi.learningservice.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.MemberTodayStudySeconds;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery.DateRange;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberCurrentTimerResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortStatisticsService {

    private final CohortAccessService cohortAccessService;
    private final CohortStatisticsRepository cohortStatisticsRepository;
    private final StudyRecordAggregationQueryService studyRecordAggregationQueryService;
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

        // 활성 수강생별 오늘 확정 학습 통계 DB 조회
        List<MemberTodayStudySeconds> confirmedDurations =
                cohortStatisticsRepository.findTodayStudySeconds(
                        cohortId,
                        aggregationDate
                );
        Map<Long, MemberCurrentTimerResult> timersByMembershipId = currentTimers(
                confirmedDurations,
                calculatedAt
        );
        List<Long> memberTotals = confirmedDurations.stream()
                .map(duration -> {
                    MemberCurrentTimerResult timer = timersByMembershipId.get(
                            duration.cohortMembershipId()
                    );
                    long runningSeconds = timer == null
                            ? 0L
                            : timer.currentAggregationSeconds();
                    return Math.addExact(duration.studySeconds(), runningSeconds);
                })
                .toList();

        // 실행 중 타이머가 반영된 합계와 참여 인원 계산
        long totalStudySeconds = memberTotals.stream()
                .mapToLong(Long::longValue)
                .sum();
        long participantCount = memberTotals.stream()
                .filter(studySeconds -> studySeconds > 0L)
                .count();
        long activeStudentCount = memberTotals.size();
        long noRecordStudentCount = activeStudentCount - participantCount;

        // 참여자 평균 계산 및 응답 결과 조립
        long averageParticipantStudySeconds = participantCount == 0
                ? 0L
                : totalStudySeconds / participantCount;

        return new TodayResult(
                aggregationDate,
                calculatedAt,
                totalStudySeconds,
                activeStudentCount,
                participantCount,
                noRecordStudentCount,
                timersByMembershipId.size(),
                averageParticipantStudySeconds,
                durationBuckets(memberTotals)
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

    private Map<Long, MemberCurrentTimerResult> currentTimers(
            List<MemberTodayStudySeconds> confirmedDurations,
            Instant calculatedAt
    ) {
        if (confirmedDurations.isEmpty()) {
            return Map.of();
        }

        return studyRecordAggregationQueryService.getCurrentTimers(
                        confirmedDurations.stream()
                                .map(MemberTodayStudySeconds::cohortMembershipId)
                                .toList(),
                        calculatedAt
                ).stream()
                .collect(Collectors.toUnmodifiableMap(
                        MemberCurrentTimerResult::cohortMembershipId,
                        timer -> timer
                ));
    }

    private List<DurationBucketResult> durationBuckets(List<Long> memberTotals) {
        Map<StudyDurationBucket, Long> counts = new EnumMap<>(StudyDurationBucket.class);
        Arrays.stream(StudyDurationBucket.values()).forEach(bucket -> counts.put(bucket, 0L));
        memberTotals.stream()
                .map(StudyDurationBucket::from)
                .forEach(bucket -> counts.computeIfPresent(
                        bucket,
                        (key, count) -> count + 1
                ));

        return Arrays.stream(StudyDurationBucket.values())
                .map(bucket -> new DurationBucketResult(
                        bucket.name(),
                        counts.get(bucket)
                ))
                .toList();
    }

    private enum StudyDurationBucket {
        NO_RECORD,
        UNDER_ONE_HOUR,
        ONE_TO_TWO_HOURS,
        TWO_TO_FOUR_HOURS,
        FOUR_HOURS_OR_MORE;

        private static StudyDurationBucket from(long studySeconds) {
            if (studySeconds == 0) {
                return NO_RECORD;
            }
            if (studySeconds < 3_600) {
                return UNDER_ONE_HOUR;
            }
            if (studySeconds < 7_200) {
                return ONE_TO_TWO_HOURS;
            }
            if (studySeconds < 14_400) {
                return TWO_TO_FOUR_HOURS;
            }
            return FOUR_HOURS_OR_MORE;
        }
    }
}

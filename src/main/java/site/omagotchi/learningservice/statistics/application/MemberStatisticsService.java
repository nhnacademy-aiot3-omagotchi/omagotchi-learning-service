package site.omagotchi.learningservice.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.MemberReference;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.PeriodSummary;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery.DateRange;
import site.omagotchi.learningservice.statistics.application.result.*;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberCurrentTimerResult;

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
public class MemberStatisticsService {

    private final CohortAccessService cohortAccessService;
    private final MemberStatisticsRepository memberStatisticsRepository;
    private final StudyRecordAggregationQueryService studyRecordAggregationQueryService;
    private final Clock clock;

    public MemberPageResult getMembers(
            UUID managerUserId,
            Long cohortId,
            String requestedWindow,
            Integer requestedPage,
            Integer requestedSize,
            String requestedSort
    ) {
        // 관리자 권한 검증
        cohortAccessService.requireManager(cohortId, managerUserId);

        // 페이지 조회 조건 검증과 요청 기준 기간 계산
        MemberPageQuery query =
                MemberPageQuery.of(
                        requestedWindow,
                        requestedPage,
                        requestedSize,
                        requestedSort
                );
        Instant calculatedAt = clock.instant();
        DateRange dateRange = query.window().resolveAt(calculatedAt);

        // 활성 수강생 수를 먼저 조회하여 전체 페이지 범위 계산
        long totalElements = memberStatisticsRepository.countActiveStudents(cohortId);
        int totalPages = totalPages(totalElements, query.size());

        // 요청 페이지가 존재할 때만 수강생 통계 DB 조회
        List<MemberSummaryResult> confirmedItems = query.offset() >= totalElements
                ? List.of()
                : memberStatisticsRepository.findActiveStudentStatisticsPage(
                cohortId,
                dateRange.to(),
                dateRange.from(),
                dateRange.to(),
                query
        );
        List<MemberSummaryResult> items = addCurrentTimerState(
                confirmedItems,
                calculatedAt
        );

        // 페이지 조회 결과와 메타데이터 조립
        return new MemberPageResult(
                query.window().value(),
                dateRange.from(),
                dateRange.to(),
                calculatedAt,
                query.page(),
                query.size(),
                totalElements,
                totalPages,
                items
        );
    }

    public MemberOverviewResult getOverview(
            UUID managerUserId,
            Long cohortId,
            Long cohortMembershipId,
            String requestedWindow
    ) {
        // 관리자 권한 검증
        cohortAccessService.requireManager(cohortId, managerUserId);

        // 조회 window 검증
        WindowQuery window = WindowQuery.parse(requestedWindow);

        // 같은 기수의 활성 수강생인지 DB 조회로 검증
        MemberReference member = memberStatisticsRepository
                .findActiveStudent(cohortId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(
                        CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND
                ));

        // 대상 검증 후 요청 기준 시각과 기간 계산
        Instant calculatedAt = clock.instant();
        DateRange dateRange = window.resolveAt(calculatedAt);

        // 수강생의 기간 요약과 날짜별 학습 통계 DB 조회
        PeriodSummary summary =
                memberStatisticsRepository.summarizeActiveRecords(
                        cohortMembershipId,
                        dateRange.from(),
                        dateRange.to()
                );
        List<DailyTotalResult> dailyTotals = fillDailyTotals(
                dateRange,
                memberStatisticsRepository.findMemberDailyStudySeconds(
                        cohortMembershipId,
                        dateRange.from(),
                        dateRange.to()
                )
        );

        // 기간 요약과 빈 날짜가 보정된 응답 결과 조립
        return new MemberOverviewResult(
                member.cohortMembershipId(),
                member.userId(),
                window.value(),
                dateRange.from(),
                dateRange.to(),
                calculatedAt,
                summary.totalStudySeconds(),
                summary.totalStudySeconds() / window.days(),
                summary.activeStudyDays(),
                summary.recordCount(),
                summary.lastStudiedAt(),
                dailyTotals
        );
    }

    public MemberDailyRecordsResult getDailyRecords(
            UUID managerUserId,
            Long cohortId,
            Long cohortMembershipId,
            LocalDate date
    ) {
        // 관리자 권한 검증
        cohortAccessService.requireManager(cohortId, managerUserId);

        // 요청 날짜의 필수값과 현재 집계일 상한 검증
        if (date == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Instant calculatedAt = clock.instant();
        if (date.isAfter(AggregationDateTime.aggregationDate(calculatedAt))) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        // 같은 기수의 활성 수강생인지 DB 조회로 검증
        MemberReference member = memberStatisticsRepository
                .findActiveStudent(cohortId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(
                        CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND
                ));

        // 검증된 수강생의 선택 집계일 기록 DB 조회
        List<MemberDailyRecordResult> records =
                memberStatisticsRepository.findMemberDailyRecords(
                        cohortMembershipId,
                        date
                );

        // 기록 합계 계산 및 응답 결과 조립
        long totalStudySeconds = records.stream()
                .mapToLong(MemberDailyRecordResult::studySeconds)
                .sum();

        return new MemberDailyRecordsResult(
                member.cohortMembershipId(),
                member.userId(),
                date,
                calculatedAt,
                totalStudySeconds,
                records
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

    private int totalPages(long totalElements, int size) {
        // 조회 대상이 없으면 페이지도 존재하지 않음
        if (totalElements == 0) {
            return 0;
        }

        // 나머지가 있는 마지막 페이지를 포함해 전체 페이지 수 계산
        return Math.toIntExact(((totalElements - 1) / size) + 1);
    }

    private List<MemberSummaryResult> addCurrentTimerState(
            List<MemberSummaryResult> confirmedItems,
            Instant calculatedAt
    ) {
        if (confirmedItems.isEmpty()) {
            return List.of();
        }

        Map<Long, MemberCurrentTimerResult> timersByMembershipId =
                studyRecordAggregationQueryService.getCurrentTimers(
                                confirmedItems.stream()
                                        .map(MemberSummaryResult::cohortMembershipId)
                                        .toList(),
                                calculatedAt
                        ).stream()
                        .collect(Collectors.toUnmodifiableMap(
                                MemberCurrentTimerResult::cohortMembershipId,
                                timer -> timer
                        ));

        return confirmedItems.stream()
                .map(item -> {
                    MemberCurrentTimerResult timer = timersByMembershipId.get(
                            item.cohortMembershipId()
                    );
                    return timer == null
                            ? item
                            : item.withRunningTimer(timer.timerStartedAt());
                })
                .toList();
    }
}

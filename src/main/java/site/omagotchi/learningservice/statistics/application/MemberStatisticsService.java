package site.omagotchi.learningservice.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.MemberReference;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.PeriodSummary;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery;
import site.omagotchi.learningservice.statistics.application.query.WindowQuery.DateRange;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordsResult;
import site.omagotchi.learningservice.statistics.application.result.MemberOverviewResult;
import site.omagotchi.learningservice.statistics.application.result.MemberPageResult;
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
public class MemberStatisticsService {

    private final CohortAccessService cohortAccessService;
    private final MemberStatisticsRepository memberStatisticsRepository;
    private final Clock clock;

    public MemberPageResult getMembers(
            UUID managerUserId,
            Long cohortId,
            String requestedWindow,
            Integer requestedPage,
            Integer requestedSize,
            String requestedSort
    ) {
        cohortAccessService.requireManager(cohortId, managerUserId);

        MemberPageQuery query =
                MemberPageQuery.of(
                        requestedWindow,
                        requestedPage,
                        requestedSize,
                        requestedSort
                );
        Instant calculatedAt = clock.instant();
        DateRange dateRange = query.window().resolveAt(calculatedAt);
        List<MemberSummaryResult> items = memberStatisticsRepository
                .findActiveStudentStatisticsPage(
                        cohortId,
                        dateRange.to(),
                        dateRange.from(),
                        dateRange.to(),
                        query
                );
        long totalElements = memberStatisticsRepository.countActiveStudents(cohortId);
        int totalPages = totalPages(totalElements, query.size());

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
        cohortAccessService.requireManager(cohortId, managerUserId);

        WindowQuery window = WindowQuery.parse(requestedWindow);
        MemberReference member = memberStatisticsRepository
                .findActiveStudent(cohortId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(
                        CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND
                ));
        Instant calculatedAt = clock.instant();
        DateRange dateRange = window.resolveAt(calculatedAt);
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
        cohortAccessService.requireManager(cohortId, managerUserId);

        MemberReference member = memberStatisticsRepository
                .findActiveStudent(cohortId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(
                        CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND
                ));
        Instant calculatedAt = clock.instant();
        if (date == null || date.isAfter(StudyTimePolicy.aggregationDate(calculatedAt))) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        List<MemberDailyRecordResult> records =
                memberStatisticsRepository.findMemberDailyRecords(
                        cohortMembershipId,
                        date
                );
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

    private int totalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact(((totalElements - 1) / size) + 1);
    }
}

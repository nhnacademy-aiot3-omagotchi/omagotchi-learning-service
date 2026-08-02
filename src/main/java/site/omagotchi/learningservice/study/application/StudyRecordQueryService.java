package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.DailyStudyRecordsResult;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.MonthlyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRecordQueryService {

    private final CohortAccessService cohortAccessService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final Clock clock;

    public StudyRecordResult getRecord(
            UUID userId,
            Long cohortId,
            UUID studyRecordId
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        StudyRecord entity = studyRecordQueryRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        return StudyRecordResult.from(entity);
    }

    public DailyStudyRecordsResult getDailyRecords(
            UUID userId,
            Long cohortId,
            LocalDate aggregationDate
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        // 날짜 범위 검증
        validateDateRange(aggregationDate);

        List<StudyRecordResult> records = studyRecordQueryRepository
                .findDailyRecords(
                        cohortMembershipId,
                        aggregationDate
                )
                .stream()
                .map(StudyRecordResult::from)
                .toList();

        long totalStudySeconds = records.stream()
                .mapToLong(StudyRecordResult::studySeconds)
                .sum();

        return new DailyStudyRecordsResult(
                aggregationDate,
                totalStudySeconds,
                records
        );
    }

    public MonthlyStudySecondsResult getMonthlyStudySeconds(
            UUID userId,
            Long cohortId,
            YearMonth aggregationMonth
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        LocalDate currentAggregationDate = currentAggregationDate();
        // 날짜 범위 검증
        validateMonthRange(aggregationMonth, currentAggregationDate);

        LocalDate startDate = aggregationMonth.atDay(1);
        LocalDate endDate = aggregationMonth.atEndOfMonth();
        LocalDate queryEndDate = min(endDate, currentAggregationDate);

        Map<LocalDate, Long> studySecondsByDate = studyRecordQueryRepository
                .findDailyStudySeconds(
                        cohortMembershipId,
                        startDate,
                        queryEndDate
                )
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        DailyStudySecondsResult::aggregationDate,
                        DailyStudySecondsResult::studySeconds
                ));

        List<DailyStudySecondsResult> dailyTotals = IntStream
                .rangeClosed(1, aggregationMonth.lengthOfMonth())
                .mapToObj(aggregationMonth::atDay)
                .map(date -> new DailyStudySecondsResult(
                        date,
                        studySecondsByDate.getOrDefault(date, 0L)
                ))
                .toList();

        long totalStudySeconds = dailyTotals.stream()
                .mapToLong(DailyStudySecondsResult::studySeconds)
                .sum();

        return new MonthlyStudySecondsResult(
                aggregationMonth,
                totalStudySeconds,
                dailyTotals
        );
    }

    private void validateDateRange(LocalDate aggregationDate) {
        if (aggregationDate.isAfter(currentAggregationDate())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        // KDT 범위 검증(Optional)
    }

    private void validateMonthRange(
            YearMonth aggregationMonth,
            LocalDate currentAggregationDate
    ) {
        if (aggregationMonth.isAfter(YearMonth.from(currentAggregationDate))) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        // KDT 범위 검증(Optional)
    }

    private LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private LocalDate currentAggregationDate() {
        return StudyTimePolicy.aggregationDate(clock.instant());
    }
}

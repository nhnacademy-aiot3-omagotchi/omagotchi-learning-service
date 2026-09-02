package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyTimeSummaryResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 총 학습 시간처럼 수치만 필요한 질문을 위한 가벼운 조회
 * 세션·시작 시각·몰입 밀도까지 필요한 경우에는 StudyPatternQueryService를 사용한다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyTimeSummaryQueryService {

    private static final int DEFAULT_PERIOD_DAYS = 7;
    private static final int MIN_PERIOD_DAYS = 1;
    private static final int MAX_PERIOD_DAYS = 90;

    private final CohortAccessService cohortAccessService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final Clock clock;

    public StudyTimeSummaryResult getSummary(UUID userId, Integer periodDaysOrNull) {
        int periodDays = resolvePeriodDays(periodDaysOrNull);
        Long membershipId = cohortAccessService.requireCurrentActiveMembership(userId).getId();

        LocalDate endDate = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate startDate = endDate.minusDays(periodDays - 1L);
        List<DailyStudySecondsResult> dailyResults = studyRecordQueryRepository
                .findDailyStudySeconds(membershipId, startDate, endDate);

        long totalStudySeconds = 0;
        int studyDayCount = 0;
        for (DailyStudySecondsResult daily : dailyResults) {
            if (daily.studySeconds() > 0) {
                totalStudySeconds += daily.studySeconds();
                studyDayCount++;
            }
        }

        if (studyDayCount == 0) {
            return StudyTimeSummaryResult.noData(periodDays);
        }

        long totalStudyMinutes = totalStudySeconds / 60;
        return new StudyTimeSummaryResult(
                StudyTimeSummaryResult.Status.OK,
                periodDays,
                totalStudyMinutes,
                studyDayCount,
                totalStudyMinutes / studyDayCount
        );
    }

    private int resolvePeriodDays(Integer periodDaysOrNull) {
        if (Objects.isNull(periodDaysOrNull)) {
            return DEFAULT_PERIOD_DAYS;
        }
        if (periodDaysOrNull < MIN_PERIOD_DAYS || periodDaysOrNull > MAX_PERIOD_DAYS) {
            throw new BusinessException(StudyRecordErrorCode.INVALID_TIME_SUMMARY_PERIOD);
        }
        return periodDaysOrNull;
    }
}

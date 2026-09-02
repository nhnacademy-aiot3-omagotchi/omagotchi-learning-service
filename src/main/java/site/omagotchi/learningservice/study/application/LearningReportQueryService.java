package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.LearningReportResult;
import site.omagotchi.learningservice.study.application.result.StudyEnvironmentResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningReportQueryService {

    // 리포트는 "주간" 콘셉트라 기본 7일. 패턴 조회 툴의 기본값(30일)과는 별개다
    private static final int REPORT_DEFAULT_PERIOD_DAYS = 7;
    // 환경 분석은 시간 단위 센서 평균을 쓰므로 7일까지만 가능하다
    private static final int MAX_ENVIRONMENT_DAYS = 7;

    private final TopLearnerPatternQueryService topLearnerPatternQueryService;
    private final StudyEnvironmentAnalysisService studyEnvironmentAnalysisService;
    private final CohortAccessService cohortAccessService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final Clock clock;

    public LearningReportResult getReport(UUID userId, Integer periodDaysOrNull) {
        // 1. 기간 확정: 사용자가 안 정했으면 리포트 기본값(7일)을 명시적으로 채운다
        Integer periodDaysToUse = periodDaysOrNull;
        if (periodDaysToUse == null) {
            periodDaysToUse = REPORT_DEFAULT_PERIOD_DAYS;
        }

        // 2. 이번 기간: 내 패턴 + 상위권 비교를 통째로 재사용 (기간 범위 검증도 이 안에서 끝난다)
        TopLearnerPatternResult thisPeriod =
                topLearnerPatternQueryService.getTopLearnerPattern(userId, periodDaysToUse);
        int periodDays = thisPeriod.periodDays();

        // 3. 직전 기간: 이번 기간 바로 앞의 같은 길이 구간
        //    이번이 22~28일(7일)이면 직전은 15~21일
        Long membershipId = cohortAccessService.requireCurrentActiveMembership(userId).getId();
        LocalDate today = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate previousEnd = today.minusDays(periodDays);
        LocalDate previousStart = previousEnd.minusDays(periodDays - 1L);

        List<DailyStudySecondsResult> previousDaily = studyRecordQueryRepository
                .findDailyStudySeconds(membershipId, previousStart, previousEnd);

        // 4. 직전 기간의 총 공부 시간과 학습일 수를 합산한다
        long previousTotalSeconds = 0;
        int previousStudyDayCount = 0;
        for (DailyStudySecondsResult daily : previousDaily) {
            if (daily.studySeconds() > 0) {
                previousTotalSeconds = previousTotalSeconds + daily.studySeconds();
                previousStudyDayCount = previousStudyDayCount + 1;
            }
        }

        // 5. 환경 분석: 어느 공간에서 했고 그때 공기가 어땠는지
        //    센서 해상도 한계로 최대 7일이라, 그보다 긴 리포트에서는 7일치만 본다
        int environmentDays = Math.min(periodDays, MAX_ENVIRONMENT_DAYS);
        StudyEnvironmentResult environment =
                studyEnvironmentAnalysisService.analyze(userId, environmentDays);

        return new LearningReportResult(
                periodDays,
                previousTotalSeconds / 60,
                previousStudyDayCount,
                thisPeriod,
                environment
        );
    }
}

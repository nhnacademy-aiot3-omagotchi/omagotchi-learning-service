package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyPatternQueryService {

    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MIN_PERIOD_DAYS = 1;
    private static final int MAX_PERIOD_DAYS = 90;

    private final CohortAccessService cohortAccessService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final Clock clock;

    public StudyPatternResult getPattern(UUID userId, Integer periodDaysOrNull) {
        int periodDays = resolvePeriodDays(periodDaysOrNull);
        Long membershipId = cohortAccessService.requireCurrentActiveMembership(userId).getId();

        LocalDate today = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate startDate = today.minusDays(periodDays - 1L);

        List<StudyRecord> records = studyRecordQueryRepository
                .findActiveRecordsBetween(membershipId, startDate, today);
        if (records.isEmpty()) {
            return StudyPatternResult.noData(periodDays);
        }

        // 날짜별로 기록을 묶는다: 날짜 → 그날의 기록 목록
        Map<LocalDate, List<StudyRecord>> byDate = new HashMap<>();
        for (StudyRecord record : records) {
            List<StudyRecord> daily = byDate.get(record.getAggregationDate());
            if (daily == null) {
                daily = new ArrayList<>();
                byDate.put(record.getAggregationDate(), daily);
            }
            daily.add(record);
        }

        // 총 공부 시간과 최장 세션
        long totalStudySeconds = 0;
        long longestSessionSeconds = 0;
        for (StudyRecord record : records) {
            totalStudySeconds = totalStudySeconds + record.getStudySeconds();
            if (record.getStudySeconds() > longestSessionSeconds) {
                longestSessionSeconds = record.getStudySeconds();
            }
        }

        // 밀도의 분모는 "자리에 있던 시간" — 날마다 첫 세션 시작~마지막 세션 종료를 더한다.
        // 세션 길이를 더하면 세션 사이에 쉰 시간이 빠져 항상 100%가 나온다
        long totalSpanSeconds = 0;
        for (List<StudyRecord> daily : byDate.values()) {
            totalSpanSeconds = totalSpanSeconds + daySpanSeconds(daily);
        }

        return new StudyPatternResult(
                StudyPatternResult.Status.OK,
                periodDays,
                byDate.size(),
                totalStudySeconds / 60,
                records.size(),
                totalStudySeconds / records.size() / 60,
                longestSessionSeconds / 60,
                typicalStartTime(byDate),
                bestStartHour(records),
                StudyPatternMath.focusDensityPercent(totalStudySeconds, totalSpanSeconds),
                currentStreakDays(byDate.keySet(), today)
        );
    }

    /** 그날 자리에 있던 시간: 첫 세션 시작부터 마지막 세션 종료까지. */
    private long daySpanSeconds(List<StudyRecord> daily) {
        Instant first = null;
        Instant last = null;
        for (StudyRecord record : daily) {
            if (first == null || record.getStartTime().isBefore(first)) {
                first = record.getStartTime();
            }
            if (last == null || record.getEndTime().isAfter(last)) {
                last = record.getEndTime();
            }
        }
        return StudyPatternMath.spanSeconds(first, last);
    }

    /** LLM이 채우는 값이라 신뢰하지 않는다. 미지정이면 기본값, 범위 밖이면 거부. */
    private int resolvePeriodDays(Integer periodDaysOrNull) {
        if (periodDaysOrNull == null) {
            return DEFAULT_PERIOD_DAYS;
        }
        if (periodDaysOrNull < MIN_PERIOD_DAYS || periodDaysOrNull > MAX_PERIOD_DAYS) {
            throw new BusinessException(StudyRecordErrorCode.INVALID_PATTERN_PERIOD);
        }
        return periodDaysOrNull;
    }

    /** 날짜마다 첫 세션의 시작 시각(분)을 모아 중앙값을 "HH:mm"으로 돌려준다. */
    private String typicalStartTime(Map<LocalDate, List<StudyRecord>> byDate) {
        List<Integer> shiftedMinutes = new ArrayList<>();

        for (List<StudyRecord> daily : byDate.values()) {
            // 그날의 첫 세션(시작 시각이 가장 이른 기록)을 찾는다
            StudyRecord first = daily.get(0);
            for (StudyRecord record : daily) {
                if (record.getStartTime().isBefore(first.getStartTime())) {
                    first = record;
                }
            }
            shiftedMinutes.add(StudyPatternMath.toShiftedMinutes(first.getStartTime()));
        }

        return StudyPatternMath.medianStartTime(shiftedMinutes);
    }

    /** 세션의 공부 시간을 시작 시각대에 귀속시켜, 시작 성과가 가장 좋은 시각대를 찾는다. */
    private Integer bestStartHour(List<StudyRecord> records) {
        // 시각대 → 누적 공부 초
        Map<Integer, Long> secondsByHour = new HashMap<>();
        for (StudyRecord record : records) {
            int hour = record.getStartTime().atZone(DateTimePolicy.ZONE_ID).getHour();
            Long accumulated = secondsByHour.get(hour);
            if (accumulated == null) {
                accumulated = 0L;
            }
            secondsByHour.put(hour, accumulated + record.getStudySeconds());
        }

        Integer bestHour = null;
        long bestSeconds = -1;
        for (Map.Entry<Integer, Long> entry : secondsByHour.entrySet()) {
            if (entry.getValue() > bestSeconds) {
                bestSeconds = entry.getValue();
                bestHour = entry.getKey();
            }
        }
        return bestHour;
    }

    /** 오늘(기록 없으면 어제)부터 거꾸로 세어, 끊기지 않고 이어진 학습일 수. */
    private int currentStreakDays(Set<LocalDate> studyDates, LocalDate today) {
        LocalDate cursor = studyDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (studyDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
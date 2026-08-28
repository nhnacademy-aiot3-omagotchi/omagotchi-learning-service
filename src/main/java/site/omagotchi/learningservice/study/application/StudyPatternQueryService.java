package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyPatternQueryService {

    private static final int DEFAULT_PERIOD_DAYS = 14;
    private static final int MIN_PERIOD_DAYS = 1;
    private static final int MAX_PERIOD_DAYS = 90;
    // AggregationDateTime과 같은 KST 기준. 시각대 계산에 필요해 여기서도 선언한다
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    // 집계일이 새벽 4시에 시작하므로, 시작 시각 중앙값도 4시를 하루의 원점으로 계산한다
    private static final int RESET_MINUTES = 4 * 60;

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

        // 총 공부 시간과 최장 세션을 한 바퀴에 계산한다
        long totalStudySeconds = 0;
        long longestSessionSeconds = 0;
        for (StudyRecord record : records) {
            totalStudySeconds = totalStudySeconds + record.getStudySeconds();
            if (record.getStudySeconds() > longestSessionSeconds) {
                longestSessionSeconds = record.getStudySeconds();
            }
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
                currentStreakDays(byDate.keySet(), today)
        );
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

            LocalTime time = first.getStartTime().atZone(ZONE_ID).toLocalTime();
            int minutesOfDay = time.getHour() * 60 + time.getMinute();
            // 새벽 0~4시 시작은 "그날의 늦은 밤"으로 취급되도록 4시 원점으로 이동
            shiftedMinutes.add((minutesOfDay - RESET_MINUTES + 1440) % 1440);
        }

        Collections.sort(shiftedMinutes);
        int median = shiftedMinutes.get(shiftedMinutes.size() / 2);
        int minutesOfDay = (median + RESET_MINUTES) % 1440;
        return String.format("%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    /** 세션의 공부 시간을 시작 시각대에 귀속시켜, 시작 성과가 가장 좋은 시각대를 찾는다. */
    private Integer bestStartHour(List<StudyRecord> records) {
        // 시각대 → 누적 공부 초
        Map<Integer, Long> secondsByHour = new HashMap<>();
        for (StudyRecord record : records) {
            int hour = record.getStartTime().atZone(ZONE_ID).getHour();
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
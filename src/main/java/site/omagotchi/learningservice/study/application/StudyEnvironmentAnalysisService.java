package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.result.PresenceIntervalView;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.SpaceEnvironmentSeries;
import site.omagotchi.learningservice.study.application.result.StudyEnvironmentResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 학습 세션 · 체류 공간 · 센서 환경을 하나로 묶어 "어디서 할 때 잘 됐는가"를 낸다.
 *
 * <p>세 재료의 출처가 모두 다르다. 세션은 study, 공간은 attendance의 체류 구간, 환경값은
 * sensor다. 이 서비스는 각 파트의 공개 계약만 부르고, 겹침 판정과 집계만 직접 한다.</p>
 *
 * <p>이산화탄소·온도·습도를 함께 본다. 어느 하나가 아니라 셋의 조합이 집중과 졸음을
 * 좌우하기 때문이다 — 예컨대 같은 온도라도 습도가 높으면 졸음 호소가 늘어난다. 다만
 * "몇 이상이면 나쁘다"는 판정은 여기서 하지 않고, 해석하는 쪽(도구 설명)이 갖는다.</p>
 *
 * <p>기간이 최대 7일인 것은 센서 조회의 해상도 때문이다. 7일까지는 시간 단위 평균이지만
 * 그보다 길면 일 단위 평균이라 세션(보통 1~2시간)과 맞물리지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyEnvironmentAnalysisService {

    private static final int DEFAULT_PERIOD_DAYS = 7;
    private static final int MIN_PERIOD_DAYS = 1;
    private static final int MAX_PERIOD_DAYS = 7;
    private static final List<String> MEASUREMENTS = List.of("co2", "temperature", "humidity");
    private static final String SERIES_WINDOW = "week";
    private static final long SLOT_SECONDS = 3600L;

    private final CohortAccessService cohortAccessService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final AttendancePresenceQueryService attendancePresenceQueryService;
    private final SpaceEnvironmentQueryService spaceEnvironmentQueryService;
    private final Clock clock;

    public StudyEnvironmentResult analyze(UUID userId, Integer periodDaysOrNull) {
        int periodDays = resolvePeriodDays(periodDaysOrNull);

        CohortMembership membership = cohortAccessService.requireCurrentActiveMembership(userId);
        Long membershipId = membership.getId();
        Long cohortId = membership.getCohortId();

        LocalDate today = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate startDate = today.minusDays(periodDays - 1L);

        // 1. 기간 내 내 학습 세션
        List<StudyRecord> records = studyRecordQueryRepository
                .findActiveRecordsBetween(membershipId, startDate, today);
        if (records.isEmpty()) {
            return StudyEnvironmentResult.noData(periodDays);
        }

        // 2. 같은 기간의 체류 구간 (어느 공간에 있었는지)
        Instant from = AggregationDateTime.startOfAggregationDate(startDate);
        Instant toExclusive = clock.instant();
        List<PresenceIntervalView> intervals = attendancePresenceQueryService
                .findPresenceIntervals(membershipId, from, toExclusive);

        // 3. 세션마다 공간을 붙인다. 겹친 시간이 가장 긴 구간의 공간을 그 세션의 공간으로 본다
        Map<Long, List<StudyRecord>> recordsBySpace = new HashMap<>();
        int unknownSpaceSessionCount = 0;
        for (StudyRecord record : records) {
            Long spaceId = findSpaceId(record, intervals);
            if (spaceId == null) {
                unknownSpaceSessionCount = unknownSpaceSessionCount + 1;
                continue;
            }
            List<StudyRecord> spaceRecords = recordsBySpace.get(spaceId);
            if (spaceRecords == null) {
                spaceRecords = new ArrayList<>();
                recordsBySpace.put(spaceId, spaceRecords);
            }
            spaceRecords.add(record);
        }
        if (recordsBySpace.isEmpty()) {
            return StudyEnvironmentResult.noSpaceData(periodDays, unknownSpaceSessionCount);
        }

        // 4. 공간마다 측정항목별 시간대 환경값을 가져온다
        Map<Long, Map<String, SpaceEnvironmentSeries>> seriesBySpace = new HashMap<>();
        for (Long spaceId : recordsBySpace.keySet()) {
            Map<String, SpaceEnvironmentSeries> byMeasurement = new HashMap<>();
            for (String measurement : MEASUREMENTS) {
                byMeasurement.put(measurement, spaceEnvironmentQueryService.getHourlySeries(
                        cohortId, userId, spaceId, measurement, SERIES_WINDOW));
            }
            seriesBySpace.put(spaceId, byMeasurement);
        }

        // 5. 공간별 성과와, 측정항목별 세션 목록을 함께 모은다
        List<StudyEnvironmentResult.SpacePerformance> spaces = new ArrayList<>();
        Map<String, List<SessionValue>> sessionsByMeasurement = new HashMap<>();
        for (String measurement : MEASUREMENTS) {
            sessionsByMeasurement.put(measurement, new ArrayList<>());
        }
        int analyzedSessionCount = 0;

        for (Map.Entry<Long, List<StudyRecord>> entry : recordsBySpace.entrySet()) {
            Long spaceId = entry.getKey();
            List<StudyRecord> spaceRecords = entry.getValue();
            Map<String, SpaceEnvironmentSeries> seriesByMeasurement = seriesBySpace.get(spaceId);

            long studySeconds = 0;
            long occupiedSeconds = 0;
            Map<String, Double> valueSums = new HashMap<>();
            Map<String, Integer> valueCounts = new HashMap<>();

            for (StudyRecord record : spaceRecords) {
                studySeconds = studySeconds + record.getStudySeconds();
                occupiedSeconds = occupiedSeconds + occupiedSecondsOf(record);

                boolean measured = false;
                for (String measurement : MEASUREMENTS) {
                    Double value = averageValueOf(record, seriesByMeasurement.get(measurement));
                    if (value == null) {
                        continue;
                    }
                    measured = true;
                    sessionsByMeasurement.get(measurement).add(new SessionValue(record, value));
                    valueSums.merge(measurement, value, Double::sum);
                    valueCounts.merge(measurement, 1, Integer::sum);
                }
                if (measured) {
                    analyzedSessionCount = analyzedSessionCount + 1;
                }
            }

            spaces.add(new StudyEnvironmentResult.SpacePerformance(
                    spaceId,
                    seriesByMeasurement.get(MEASUREMENTS.get(0)).spaceName(),
                    spaceRecords.size(),
                    studySeconds / 60,
                    StudyPatternMath.focusDensityPercent(studySeconds, occupiedSeconds),
                    averageOf(valueSums, valueCounts, "co2"),
                    averageOf(valueSums, valueCounts, "temperature"),
                    averageOf(valueSums, valueCounts, "humidity")
            ));
        }

        if (analyzedSessionCount == 0) {
            return StudyEnvironmentResult.noSensorData(periodDays, unknownSpaceSessionCount, spaces);
        }

        // 6. 측정항목마다 중앙값으로 갈라 밀도를 비교한다
        List<StudyEnvironmentResult.MeasurementContrast> contrasts = new ArrayList<>();
        for (String measurement : MEASUREMENTS) {
            List<SessionValue> sessions = sessionsByMeasurement.get(measurement);
            if (sessions.size() >= 2) {
                contrasts.add(contrastOf(measurement, sessions));
            }
        }

        return new StudyEnvironmentResult(
                StudyEnvironmentResult.Status.OK,
                periodDays,
                analyzedSessionCount,
                unknownSpaceSessionCount,
                spaces,
                contrasts
        );
    }

    /** LLM이 채우는 값이라 신뢰하지 않는다. 센서 해상도 때문에 최대 7일이다. */
    private int resolvePeriodDays(Integer periodDaysOrNull) {
        if (periodDaysOrNull == null) {
            return DEFAULT_PERIOD_DAYS;
        }
        if (periodDaysOrNull < MIN_PERIOD_DAYS || periodDaysOrNull > MAX_PERIOD_DAYS) {
            throw new BusinessException(StudyRecordErrorCode.INVALID_ENVIRONMENT_PERIOD);
        }
        return periodDaysOrNull;
    }

    private Double averageOf(
            Map<String, Double> valueSums,
            Map<String, Integer> valueCounts,
            String measurement
    ) {
        Integer count = valueCounts.get(measurement);
        if (count == null || count == 0) {
            return null;
        }
        return valueSums.get(measurement) / count;
    }

    /** 세션과 가장 오래 겹친 체류 구간의 공간. 겹치는 구간이 없으면 null이다. */
    private Long findSpaceId(StudyRecord record, List<PresenceIntervalView> intervals) {
        Long bestSpaceId = null;
        long bestOverlap = 0;

        for (PresenceIntervalView interval : intervals) {
            if (interval.spaceId() == null) {
                continue;
            }
            Instant intervalEnd = interval.endedAt();
            if (intervalEnd == null) {
                intervalEnd = clock.instant();
            }
            long overlap = overlapSeconds(
                    record.getStartTime(), record.getEndTime(),
                    interval.startedAt(), intervalEnd);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestSpaceId = interval.spaceId();
            }
        }
        return bestSpaceId;
    }

    /** 세션이 걸친 시간대들의 환경값 평균. 값이 하나도 없으면 null이다. */
    private Double averageValueOf(StudyRecord record, SpaceEnvironmentSeries series) {
        if (series == null || !series.hasValues()) {
            return null;
        }

        double sum = 0;
        int count = 0;
        for (Map.Entry<Instant, Double> slot : series.hourlyAverages().entrySet()) {
            Instant slotStart = slot.getKey();
            Instant slotEnd = slotStart.plusSeconds(SLOT_SECONDS);
            if (overlapSeconds(record.getStartTime(), record.getEndTime(), slotStart, slotEnd) > 0) {
                sum = sum + slot.getValue();
                count = count + 1;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    /** 환경값 중앙값으로 세션을 둘로 갈라 몰입 밀도를 비교한다. */
    private StudyEnvironmentResult.MeasurementContrast contrastOf(
            String measurement,
            List<SessionValue> sessions
    ) {
        List<Double> values = new ArrayList<>();
        for (SessionValue session : sessions) {
            values.add(session.value());
        }
        Collections.sort(values);
        double median;
        int size = values.size();
        if (size % 2 == 1) {
            median = values.get(size / 2);
        } else {
            median = (values.get(size / 2 - 1) + values.get(size / 2)) / 2;
        }

        long lowStudySeconds = 0;
        long lowOccupiedSeconds = 0;
        double lowValueSum = 0;
        int lowCount = 0;
        long highStudySeconds = 0;
        long highOccupiedSeconds = 0;
        double highValueSum = 0;
        int highCount = 0;

        for (SessionValue session : sessions) {
            StudyRecord record = session.record();
            if (session.value() <= median) {
                lowStudySeconds = lowStudySeconds + record.getStudySeconds();
                lowOccupiedSeconds = lowOccupiedSeconds + occupiedSecondsOf(record);
                lowValueSum = lowValueSum + session.value();
                lowCount = lowCount + 1;
            } else {
                highStudySeconds = highStudySeconds + record.getStudySeconds();
                highOccupiedSeconds = highOccupiedSeconds + occupiedSecondsOf(record);
                highValueSum = highValueSum + session.value();
                highCount = highCount + 1;
            }
        }

        return new StudyEnvironmentResult.MeasurementContrast(
                measurement,
                median,
                lowCount,
                StudyPatternMath.focusDensityPercent(lowStudySeconds, lowOccupiedSeconds),
                lowCount == 0 ? 0 : lowValueSum / lowCount,
                highCount,
                StudyPatternMath.focusDensityPercent(highStudySeconds, highOccupiedSeconds),
                highCount == 0 ? 0 : highValueSum / highCount
        );
    }

    private long occupiedSecondsOf(StudyRecord record) {
        return Duration.between(record.getStartTime(), record.getEndTime()).getSeconds();
    }

    /** 두 구간이 겹친 초. 겹치지 않으면 0이다. */
    private long overlapSeconds(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd) {
        Instant start = leftStart.isAfter(rightStart) ? leftStart : rightStart;
        Instant end = leftEnd.isBefore(rightEnd) ? leftEnd : rightEnd;
        if (!start.isBefore(end)) {
            return 0;
        }
        return Duration.between(start, end).getSeconds();
    }

    /** 세션 하나와 그 세션 시간대의 환경값. 중앙값 대비를 내는 데만 쓴다. */
    private record SessionValue(StudyRecord record, double value) {
    }
}

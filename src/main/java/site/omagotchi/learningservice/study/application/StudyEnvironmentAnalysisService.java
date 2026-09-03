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
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.SpaceEnvironmentSeries;
import site.omagotchi.learningservice.study.application.result.StudyEnvironmentResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 학습 세션을 시간대·공간 블록으로 쪼개, 블록마다 밀도와 환경값을 붙인다.
 *
 * <p>세 재료의 출처가 다르다. 세션은 study, 공간은 attendance의 체류 구간, 환경값은 sensor다.
 * 이 서비스는 각 파트의 공개 계약만 부르고 겹침 판정과 집계만 직접 한다.</p>
 *
 * <p><b>시간대는 집계일 원점(KST 04:00)에 맞춰 자른다.</b> 자정 기준으로 자르면 집계일 하나가
 * 04시 조각과 다음날 새벽 조각으로 찢어져 같은 블록이 두 덩어리가 된다.</p>
 *
 * <p><b>공간은 두 단계로 정한다.</b> 체류 기록에 공간이 있으면 그것을 쓰고, 없으면 기수에
 * 배정된 실습실에 있었다고 본다(회의실을 안 썼으면 실습실이라는 규칙). 후자는 추정이므로
 * {@code spaceSource}에 표시해 받는 쪽이 단정하지 않게 한다.</p>
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
    // 체크아웃을 안 한 구간을 언제까지 인정할지. 타이머 한 세션 상한(12시간)보다 조금 길게 잡는다
    private static final Duration MAX_OPEN_STAY = Duration.ofHours(14);

    // 집계일 원점(04:00)에서 몇 시간째인지로 시간대를 나눈다. 마지막 밴드가 다음날 04시까지다
    private static final int[] BAND_START_HOURS = {0, 5, 9, 14};
    private static final int[] BAND_END_HOURS = {5, 9, 14, 24};
    private static final String[] BAND_LABELS = {
            "새벽·아침(04-09시)", "오전(09-13시)", "오후(13-18시)", "저녁·밤(18-04시)"
    };

    private final CohortAccessService cohortAccessService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final AttendancePresenceQueryService attendancePresenceQueryService;
    private final SpaceEnvironmentQueryService spaceEnvironmentQueryService;
    private final SpaceQueryService spaceQueryService;
    private final Clock clock;

    /** 계정만 아는 호출자를 위한 진입점 (소속을 스스로 구함) */
    public StudyEnvironmentResult analyze(UUID userId, Integer periodDaysOrNull) {
        // 기간 검증을 소속 조회보다 먼저 한다. 잘못된 기간에 DB를 치지 않는다
        int periodDays = resolvePeriodDays(periodDaysOrNull);
        return this.analyze(
                cohortAccessService.requireCurrentActiveMembership(userId), periodDays);
    }

    /** 이미 활성 소속을 구한 호출자(리포트)를 위한 진입점 */
    public StudyEnvironmentResult analyze(CohortMembership membership, Integer periodDaysOrNull) {
        int periodDays = resolvePeriodDays(periodDaysOrNull);

        Long membershipId = membership.getId();
        Long cohortId = membership.getCohortId();
        UUID userId = membership.getUserId();

        LocalDate today = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate startDate = today.minusDays(periodDays - 1L);

        // 1. 기간 내 학습 세션
        List<StudyRecord> records = studyRecordQueryRepository
                .findActiveRecordsBetween(membershipId, startDate, today);
        if (records.isEmpty()) {
            return StudyEnvironmentResult.noData(periodDays);
        }

        // 2. 같은 기간의 체류 구간
        Instant from = AggregationDateTime.startOfAggregationDate(startDate);
        Instant toExclusive = clock.instant();
        List<PresenceIntervalView> intervals = attendancePresenceQueryService
                .findPresenceIntervals(membershipId, from, toExclusive);

        // 3. 공간을 못 찾을 때 쓸 기수 실습실
        SpaceListResult fallbackLab = findCohortLab(userId, cohortId);

        // 4. 세션을 (집계일 × 시간대 × 공간) 블록으로 쪼개 담는다
        Map<String, Block> blocks = new LinkedHashMap<>();
        int unknownSpaceSessionCount = 0;
        boolean usedPresence = false;
        boolean usedFallback = false;

        for (StudyRecord record : records) {
            Long spaceId = findSpaceId(record, intervals, toExclusive);
            String spaceName = null;
            if (spaceId != null) {
                usedPresence = true;
            } else if (fallbackLab != null) {
                spaceId = fallbackLab.spaceId();
                spaceName = fallbackLab.name();
                usedFallback = true;
            } else {
                unknownSpaceSessionCount = unknownSpaceSessionCount + 1;
                continue;
            }
            addToBlocks(blocks, record, spaceId, spaceName);
        }
        if (blocks.isEmpty()) {
            return StudyEnvironmentResult.noSpaceData(periodDays, unknownSpaceSessionCount);
        }

        // 5. 블록에 등장한 공간마다 항목별 시계열을 한 번씩 가져온다.
        //    공간 이름 사전은 여기서 한 번만 읽는다 — 항목마다 다시 읽으면 같은 조회가 반복된다
        Map<Long, String> spaceNames = spaceEnvironmentQueryService.findSpaceNames();
        Map<Long, Map<String, SpaceEnvironmentSeries>> seriesBySpace = new HashMap<>();
        for (Block block : blocks.values()) {
            if (seriesBySpace.containsKey(block.spaceId)) {
                continue;
            }
            String spaceName = spaceNames.get(block.spaceId);
            Map<String, SpaceEnvironmentSeries> byMeasurement = new HashMap<>();
            for (String measurement : MEASUREMENTS) {
                byMeasurement.put(measurement, spaceEnvironmentQueryService.getHourlySeries(
                        cohortId, userId, block.spaceId, spaceName, measurement, SERIES_WINDOW));
            }
            seriesBySpace.put(block.spaceId, byMeasurement);
        }

        // 6. 블록마다 그 시간대의 환경 평균을 붙인다
        boolean anyMeasured = false;
        for (Block block : blocks.values()) {
            Map<String, SpaceEnvironmentSeries> byMeasurement = seriesBySpace.get(block.spaceId);
            block.co2 = blockAverage(block, byMeasurement.get("co2"));
            block.temperature = blockAverage(block, byMeasurement.get("temperature"));
            block.humidity = blockAverage(block, byMeasurement.get("humidity"));
            if (block.co2 != null || block.temperature != null || block.humidity != null) {
                anyMeasured = true;
            }
            if (block.spaceName == null) {
                block.spaceName = byMeasurement.get("co2").spaceName();
            }
        }

        // 7. 시간대별·공간별로 합산한다
        List<StudyEnvironmentResult.BlockSummary> timeBands = summarize(blocks.values(), true);
        List<StudyEnvironmentResult.BlockSummary> spaces = summarize(blocks.values(), false);

        int analyzedSessionCount = records.size() - unknownSpaceSessionCount;
        String spaceSource = "NONE";
        if (usedPresence) {
            spaceSource = "PRESENCE";
        } else if (usedFallback) {
            spaceSource = "COHORT_LAB";
        }

        if (!anyMeasured) {
            return StudyEnvironmentResult.noSensorData(periodDays, spaceSource,
                    analyzedSessionCount, unknownSpaceSessionCount, timeBands, spaces);
        }
        return new StudyEnvironmentResult(
                StudyEnvironmentResult.Status.OK,
                periodDays,
                spaceSource,
                analyzedSessionCount,
                unknownSpaceSessionCount,
                timeBands,
                spaces
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

    /** 기수에 배정된 운영 중인 실습실. 없으면 null. */
    private SpaceListResult findCohortLab(UUID userId, Long cohortId) {
        for (SpaceListResult space : spaceQueryService.getSpaceList(userId)) {
            if (!cohortId.equals(space.cohortId())) {
                continue;
            }
            if (space.spaceType() != SpaceType.LAB) {
                continue;
            }
            if (space.operationalStatus() != SpaceOperationalStatus.ACTIVE) {
                continue;
            }
            return space;
        }
        return null;
    }

    /** 세션을 시간대 경계로 쪼개 블록에 더한다. 공부 시간은 겹친 시간 비율로 나눈다. */
    private void addToBlocks(
            Map<String, Block> blocks,
            StudyRecord record,
            Long spaceId,
            String spaceName
    ) {
        LocalDate date = record.getAggregationDate();
        Instant origin = AggregationDateTime.startOfAggregationDate(date);
        long sessionSeconds = Duration.between(record.getStartTime(), record.getEndTime()).getSeconds();
        if (sessionSeconds <= 0) {
            return;
        }

        for (int band = 0; band < BAND_LABELS.length; band++) {
            Instant bandStart = origin.plusSeconds(BAND_START_HOURS[band] * 3600L);
            Instant bandEnd = origin.plusSeconds(BAND_END_HOURS[band] * 3600L);

            long overlap = overlapSeconds(
                    record.getStartTime(), record.getEndTime(), bandStart, bandEnd);
            if (overlap <= 0) {
                continue;
            }

            String key = date + "|" + band + "|" + spaceId;
            Block block = blocks.get(key);
            if (block == null) {
                block = new Block(band, spaceId, spaceName, bandStart, bandEnd);
                blocks.put(key, block);
            }

            // 세션이 밴드를 걸치면 공부 시간도 겹친 비율만큼만 이 블록의 몫이다
            block.studySeconds = block.studySeconds
                    + record.getStudySeconds() * overlap / sessionSeconds;
            block.sessionCount = block.sessionCount + 1;

            Instant clippedStart = record.getStartTime().isAfter(bandStart)
                    ? record.getStartTime() : bandStart;
            Instant clippedEnd = record.getEndTime().isBefore(bandEnd)
                    ? record.getEndTime() : bandEnd;
            if (block.first == null || clippedStart.isBefore(block.first)) {
                block.first = clippedStart;
            }
            if (block.last == null || clippedEnd.isAfter(block.last)) {
                block.last = clippedEnd;
            }
        }
    }

    /** 세션과 가장 오래 겹친 체류 구간의 공간. 겹치는 구간이 없으면 null이다. */
    private Long findSpaceId(StudyRecord record, List<PresenceIntervalView> intervals, Instant now) {
        Long bestSpaceId = null;
        long bestOverlap = 0;

        for (PresenceIntervalView interval : intervals) {
            if (interval.spaceId() == null) {
                continue;
            }
            long overlap = overlapSeconds(
                    record.getStartTime(), record.getEndTime(),
                    interval.startedAt(), intervalEndOf(interval, now));
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestSpaceId = interval.spaceId();
            }
        }
        return bestSpaceId;
    }

    /**
     * 체류 구간의 끝. 체크아웃을 안 한 구간은 시작 후 {@link #MAX_OPEN_STAY}까지만 인정한다.
     *
     * <p>출결에는 열린 구간을 자동으로 닫는 배치가 없어, 퇴근 처리를 잊은 날의 구간은
     * {@code endedAt}이 영원히 비어 있다. 그것을 "지금까지"로 보면 며칠 전 구간이 오늘까지
     * 이어진 것으로 계산돼, 이후 모든 세션이 그 공간에 붙는다.</p>
     *
     * <p>집계일 경계(KST 04:00)로 자르지 않는 이유가 있다. 학습 기록은 집계일을 넘지 못하므로,
     * 새벽 1시에 체크인한 구간을 04:00에서 자르면 그날 아침 세션과 겹침이 <b>정확히 0</b>이 되어
     * 지금 실제로 자리에 있는 사람의 공간까지 못 찾는다. 체크인이 03:59냐 04:01이냐로 인정 폭이
     * 1분과 24시간을 오가는 문제도 생긴다. 그래서 시작 시각 기준 경과 시간으로 자른다.</p>
     */
    private Instant intervalEndOf(PresenceIntervalView interval, Instant now) {
        if (interval.endedAt() != null) {
            return interval.endedAt();
        }

        Instant limit = interval.startedAt().plus(MAX_OPEN_STAY);
        return limit.isBefore(now) ? limit : now;
    }

    /** 블록의 시간 구간과 겹치는 센서 슬롯들의 평균. 값이 없으면 null. */
    private Double blockAverage(Block block, SpaceEnvironmentSeries series) {
        if (series == null || !series.hasValues()) {
            return null;
        }

        double sum = 0;
        int count = 0;
        for (Map.Entry<Instant, Double> slot : series.hourlyAverages().entrySet()) {
            Instant slotStart = slot.getKey();
            Instant slotEnd = slotStart.plusSeconds(SLOT_SECONDS);
            if (overlapSeconds(block.first, block.last, slotStart, slotEnd) > 0) {
                sum = sum + slot.getValue();
                count = count + 1;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    /** 블록들을 시간대별(byBand=true) 또는 공간별로 합산한다. */
    private List<StudyEnvironmentResult.BlockSummary> summarize(
            Iterable<Block> blocks,
            boolean byBand
    ) {
        Map<String, Sum> sums = new LinkedHashMap<>();
        for (Block block : blocks) {
            String key = byBand ? BAND_LABELS[block.band] : String.valueOf(block.spaceId);
            Sum sum = sums.get(key);
            if (sum == null) {
                sum = new Sum(byBand ? BAND_LABELS[block.band] : block.spaceName,
                        byBand ? null : block.spaceId);
                sums.put(key, sum);
            }
            sum.add(block);
        }

        List<StudyEnvironmentResult.BlockSummary> summaries = new ArrayList<>();
        for (Sum sum : sums.values()) {
            summaries.add(sum.toSummary());
        }
        return summaries;
    }

    /** 두 구간이 겹친 초. 겹치지 않으면 0이다. */
    private long overlapSeconds(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd) {
        if (leftStart == null || leftEnd == null) {
            return 0;
        }
        Instant start = leftStart.isAfter(rightStart) ? leftStart : rightStart;
        Instant end = leftEnd.isBefore(rightEnd) ? leftEnd : rightEnd;
        if (!start.isBefore(end)) {
            return 0;
        }
        return Duration.between(start, end).getSeconds();
    }

    /** 집계 중인 블록 하나. 계산 중에만 쓰는 가변 상자다. */
    private static final class Block {
        private final int band;
        private final Long spaceId;
        private String spaceName;
        private long studySeconds;
        private int sessionCount;
        private Instant first;
        private Instant last;
        private Double co2;
        private Double temperature;
        private Double humidity;

        private Block(int band, Long spaceId, String spaceName, Instant bandStart, Instant bandEnd) {
            this.band = band;
            this.spaceId = spaceId;
            this.spaceName = spaceName;
        }
    }

    /** 블록들을 한 축으로 합칠 때 쓰는 누적기. */
    private static final class Sum {
        private final String label;
        private final Long spaceId;
        private long studySeconds;
        private long spanSeconds;
        private int sessionCount;
        private double co2Sum;
        private int co2Count;
        private double temperatureSum;
        private int temperatureCount;
        private double humiditySum;
        private int humidityCount;

        private Sum(String label, Long spaceId) {
            this.label = label;
            this.spaceId = spaceId;
        }

        private void add(Block block) {
            studySeconds = studySeconds + block.studySeconds;
            spanSeconds = spanSeconds + StudyPatternMath.spanSeconds(block.first, block.last);
            sessionCount = sessionCount + block.sessionCount;
            if (block.co2 != null) {
                co2Sum = co2Sum + block.co2;
                co2Count = co2Count + 1;
            }
            if (block.temperature != null) {
                temperatureSum = temperatureSum + block.temperature;
                temperatureCount = temperatureCount + 1;
            }
            if (block.humidity != null) {
                humiditySum = humiditySum + block.humidity;
                humidityCount = humidityCount + 1;
            }
        }

        private StudyEnvironmentResult.BlockSummary toSummary() {
            return new StudyEnvironmentResult.BlockSummary(
                    label,
                    spaceId,
                    sessionCount,
                    studySeconds / 60,
                    spanSeconds / 60,
                    StudyPatternMath.focusDensityPercent(studySeconds, spanSeconds),
                    co2Count == 0 ? null : co2Sum / co2Count,
                    temperatureCount == 0 ? null : temperatureSum / temperatureCount,
                    humidityCount == 0 ? null : humiditySum / humidityCount
            );
        }
    }
}

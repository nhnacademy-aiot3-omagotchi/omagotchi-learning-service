package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SpaceEnvironmentQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorReadingSnapshot;
import site.omagotchi.learningservice.sensor.application.result.SpaceEnvironmentResult;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 기수가 쓰는 공간들의 현재 실내 환경.
 *
 * <p>화면 카드가 소비처다. 카드마다 시계열을 부르면 공간 수 × 측정 항목 수만큼 왕복이 생기므로
 * 한 번에 모아 준다. 집계 규칙은 공간 시계열과 같다 — 운영 중인 기기만, 공간 안에서는 평균.</p>
 *
 * <p>공간 이름이 아니라 기기의 spaceId로 묶는다. 시계열의 location 태그는 게이트웨이가 보내는
 * 값이라 공간 이름과 어긋날 수 있고, 그때 조회는 오류 없이 빈 값만 돌려준다.</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceEnvironmentService {

    /** 카드가 쓰는 세 항목. 시계열의 _measurement 이름과 같다. */
    private static final List<String> MEASUREMENTS = List.of("co2", "temperature", "humidity");

    /**
     * 값이 아직 현재 값인지 보는 기준 = 수집 주기 × 3.
     *
     * <p>Rule Service 의 끊김 판정({@code DisconnectDetectorNode})과 같은 규칙이다. 기준이
     * 서로 다르면 한 화면은 "정상"이라 하고 다른 화면은 알림을 보내는 상태가 생긴다.
     * 주기는 기기마다 다르므로 판정도 기기별로 한다.</p>
     */
    private static final int DISCONNECT_MULTIPLIER = 3;

    /** 주기를 모르는 기기의 기본값. Rule Service 의 기본 주기와 같다. */
    private static final int DEFAULT_INTERVAL_SECONDS = 60;

    /**
     * 시계열을 거슬러 볼 최대 폭.
     *
     * <p>기기 주기에 상한이 없어 설정을 잘못 넣으면 조회 구간이 며칠로 벌어진다. 판정 자체는
     * 기기별로 하므로 이 상한은 질의 폭만 막는다 — 주기가 2시간을 넘는 기기는 이 창 밖의
     * 값을 아예 읽지 않으므로 값 없음이 된다.</p>
     */
    private static final Duration MAX_LOOKBACK = Duration.ofHours(6);

    private final SensorDeviceRepository sensorDeviceRepository;
    private final SpaceSeriesRepository spaceSeriesRepository;
    private final CohortAccessService cohortAccessService;
    private final SpaceCohortQueryService spaceCohortQueryService;
    private final Clock clock;

    /**
     * 기수 소유 공간 전부의 현재 환경.
     *
     * <p>값이 없는 공간도 목록에 남긴다 — 화면이 spaceId로 이어붙이므로 빠지면 카드가
     * 무엇을 못 받았는지 알 수 없다.</p>
     */
    public List<SpaceEnvironmentResult> getCohortEnvironments(Long cohortId, UUID requesterId) {
        cohortAccessService.requireActiveMembershipId(cohortId, requesterId);

        List<Long> spaceIds = visibleSpaceIds(cohortId);
        if (spaceIds.isEmpty()) {
            return List.of();
        }

        // 운영 중인 기기만 본다. 회수·비활성 기기는 평균에 섞이지 않는다
        List<SensorDevice> devices = sensorDeviceRepository.findActiveBySpaceIds(spaceIds);
        Map<String, SensorDevice> deviceByEui = devices.stream()
                .filter(device -> Objects.nonNull(device.getSpaceId()))
                .collect(Collectors.toMap(
                        SensorDevice::getDeviceEui,
                        device -> device,
                        (left, right) -> left));

        Map<Long, Integer> deviceCountBySpace = new HashMap<>();
        for (SensorDevice device : deviceByEui.values()) {
            deviceCountBySpace.merge(device.getSpaceId(), 1, Integer::sum);
        }

        if (deviceByEui.isEmpty()) {
            return spaceIds.stream().map(spaceId -> empty(spaceId, 0)).toList();
        }

        // 질의는 가장 느린 기기에 맞춰 한 번만 하고, 버릴지는 기기별 기준으로 가른다
        Instant now = clock.instant();
        List<SensorReadingSnapshot> readings = spaceSeriesRepository.findLatestReadings(
                new SpaceEnvironmentQuery(
                        deviceByEui.keySet(),
                        MEASUREMENTS,
                        now.minus(longestFreshness(deviceByEui.values())),
                        now
                )
        );

        Map<Long, Aggregate> bySpace = new LinkedHashMap<>();
        for (SensorReadingSnapshot reading : readings) {
            SensorDevice device = deviceByEui.get(reading.deviceEui());
            if (device == null || isStale(reading, device, now)) {
                continue;
            }
            bySpace.computeIfAbsent(device.getSpaceId(), key -> new Aggregate()).add(reading);
        }

        List<SpaceEnvironmentResult> environments = new ArrayList<>();
        for (Long spaceId : spaceIds) {
            int deviceCount = deviceCountBySpace.getOrDefault(spaceId, 0);
            Aggregate aggregate = bySpace.get(spaceId);
            environments.add(aggregate == null
                    ? empty(spaceId, deviceCount)
                    : aggregate.toResult(spaceId, deviceCount));
        }
        return environments;
    }

    /** 그 기기가 이 시간까지 안 보내면 끊긴 것으로 본다. */
    private static Duration freshnessOf(SensorDevice device) {
        Integer interval = device.getExpectedIntervalSeconds();
        long seconds = interval == null || interval <= 0 ? DEFAULT_INTERVAL_SECONDS : interval;
        return Duration.ofSeconds(seconds).multipliedBy(DISCONNECT_MULTIPLIER);
    }

    private static boolean isStale(SensorReadingSnapshot reading, SensorDevice device, Instant now) {
        return reading.time().isBefore(now.minus(freshnessOf(device)));
    }

    /** 한 번의 질의가 모든 기기를 담으려면 가장 느린 기기의 기준까지 거슬러 봐야 한다. */
    private static Duration longestFreshness(Collection<SensorDevice> devices) {
        Duration longest = devices.stream()
                .map(SpaceEnvironmentService::freshnessOf)
                .max(Comparator.naturalOrder())
                .orElse(Duration.ofSeconds((long) DEFAULT_INTERVAL_SECONDS * DISCONNECT_MULTIPLIER));

        return longest.compareTo(MAX_LOOKBACK) > 0 ? MAX_LOOKBACK : longest;
    }

    /**
     * 이 기수 화면에 나오는 공간.
     *
     * <p>배정 공간(실습실)만으로는 부족하다 — 회의실·도서관은 관리 주체 기수가 없는 공용 공간이라
     * 배정 목록에서 빠지는데, 화면에는 그대로 나오고 카드도 실내 환경 자리를 갖고 있다.</p>
     *
     * <p>다른 기수가 관리하는 공간은 넣지 않는다. 그 공간까지 필요해지면 여기만 넓히면 된다.</p>
     */
    private List<Long> visibleSpaceIds(Long cohortId) {
        Set<Long> spaceIds = new LinkedHashSet<>(
                spaceCohortQueryService.findSpaceIdsByCohortId(cohortId));
        spaceIds.addAll(spaceCohortQueryService.findUnassignedSpaceIds());
        return List.copyOf(spaceIds);
    }

    private static SpaceEnvironmentResult empty(Long spaceId, int deviceCount) {
        return new SpaceEnvironmentResult(spaceId, null, null, null, null, deviceCount);
    }

    /** 한 공간 안 기기들의 값을 항목별 평균으로 모은다. */
    private static final class Aggregate {

        private final Map<String, Mean> means = new HashMap<>();
        private Instant measuredAt;

        void add(SensorReadingSnapshot reading) {
            means.computeIfAbsent(reading.measurement(), key -> new Mean()).add(reading.value());
            if (measuredAt == null || reading.time().isAfter(measuredAt)) {
                measuredAt = reading.time();
            }
        }

        SpaceEnvironmentResult toResult(Long spaceId, int deviceCount) {
            return new SpaceEnvironmentResult(
                    spaceId,
                    mean("co2"),
                    mean("temperature"),
                    mean("humidity"),
                    measuredAt,
                    deviceCount
            );
        }

        private Double mean(String measurement) {
            Mean mean = means.get(measurement);
            return mean == null ? null : mean.value();
        }

        private static final class Mean {

            private double total;
            private int count;

            void add(double value) {
                total += value;
                count++;
            }

            Double value() {
                return count == 0 ? null : total / count;
            }
        }
    }
}

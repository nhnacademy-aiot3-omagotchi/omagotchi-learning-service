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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
     * 이보다 오래된 값은 현재 값으로 보지 않는다.
     *
     * <p>없으면 센서가 죽은 공간의 어제 값이 현재 값처럼 보인다. 기기 수집 주기
     * ({@code expectedIntervalSeconds}, 보통 분 단위)보다 넉넉히 잡아 한두 번 걸러도
     * 값이 사라지지 않게 한다.</p>
     */
    private static final Duration FRESHNESS = Duration.ofMinutes(30);

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
        Map<String, Long> spaceIdByEui = devices.stream()
                .filter(device -> Objects.nonNull(device.getSpaceId()))
                .collect(Collectors.toMap(
                        SensorDevice::getDeviceEui,
                        SensorDevice::getSpaceId,
                        (left, right) -> left));

        Map<Long, Integer> deviceCountBySpace = new HashMap<>();
        for (Long spaceId : spaceIdByEui.values()) {
            deviceCountBySpace.merge(spaceId, 1, Integer::sum);
        }

        if (spaceIdByEui.isEmpty()) {
            return spaceIds.stream().map(spaceId -> empty(spaceId, 0)).toList();
        }

        Instant now = clock.instant();
        List<SensorReadingSnapshot> readings = spaceSeriesRepository.findLatestReadings(
                new SpaceEnvironmentQuery(
                        spaceIdByEui.keySet(),
                        MEASUREMENTS,
                        now.minus(FRESHNESS),
                        now
                )
        );

        Map<Long, Aggregate> bySpace = new LinkedHashMap<>();
        for (SensorReadingSnapshot reading : readings) {
            Long spaceId = spaceIdByEui.get(reading.deviceEui());
            if (spaceId == null) {
                continue;
            }
            bySpace.computeIfAbsent(spaceId, key -> new Aggregate()).add(reading);
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

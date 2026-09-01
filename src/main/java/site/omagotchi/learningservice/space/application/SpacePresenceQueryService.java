package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpacePresenceQueryPort;
import site.omagotchi.learningservice.space.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 공간 정책이 쓰는 현재 재실·복귀 예약 집계.
 *
 * <p>집계 기준일을 여기서 한 번만 정한다. 공간 비활성화 가드와 실습실 정원 판단이 서로
 * 다른 날짜를 보지 않도록 {@link AggregationDateTime}을 이 한 곳에서 적용한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpacePresenceQueryService {

    private final SpacePresenceQueryPort spacePresenceQueryPort;
    private final Clock clock;

    public SpacePresenceSummary summarize(Long spaceId) {
        if (spaceId == null) {
            return SpacePresenceSummary.empty();
        }
        return summarize(Set.of(spaceId)).getOrDefault(spaceId, SpacePresenceSummary.empty());
    }

    public Map<Long, SpacePresenceSummary> summarize(Collection<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> distinctSpaceIds = new LinkedHashSet<>();
        spaceIds.stream()
                .filter(Objects::nonNull)
                .forEach(distinctSpaceIds::add);

        if (distinctSpaceIds.isEmpty()) {
            return Map.of();
        }
        return spacePresenceQueryPort.summarize(
                distinctSpaceIds,
                AggregationDateTime.aggregationDate(clock.instant())
        );
    }

    /** 대상 출결이 이미 이 LAB의 직접 체류 또는 회의 후 복귀 예약에 포함되는지 확인한다. */
    public boolean isReserved(Long spaceId, Long attendanceId) {
        if (spaceId == null || attendanceId == null) {
            return false;
        }
        return spacePresenceQueryPort.isReserved(
                spaceId,
                attendanceId,
                AggregationDateTime.aggregationDate(clock.instant())
        );
    }
}

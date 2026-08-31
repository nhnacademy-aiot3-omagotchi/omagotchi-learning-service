package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.port.AttendanceSpacePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * attendance 기능이 소유한 체류 구간을 공간 정책에 제공하는 공개 조회 계약.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresenceSpaceQueryService {

    private final AttendanceSpacePresenceQuery attendanceSpacePresenceQuery;
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
        return attendanceSpacePresenceQuery.summarize(
                distinctSpaceIds,
                AggregationDateTime.aggregationDate(clock.instant())
        );
    }
}

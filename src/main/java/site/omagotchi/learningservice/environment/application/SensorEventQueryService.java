package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.environment.application.port.SensorEventStore;
import site.omagotchi.learningservice.environment.application.query.SensorEventItem;
import site.omagotchi.learningservice.environment.application.query.SensorEventPage;
import site.omagotchi.learningservice.environment.application.query.SensorEventQuery;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.result.SensorDeviceResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 레디스 조회 서비스 */
@RequiredArgsConstructor
@Service
public class SensorEventQueryService {

    private final SensorEventStore sensorEventStore;
    private final SensorDeviceService sensorDeviceService;
    private final Clock clock;

    /** 기수에 속한 센서 이벤트를 페이지 단위로 조회하고 표시명을 결합한다. 매니저만. */
    public SensorEventPage getEvents(
            Long cohortId,
            UUID requesterId,
            SensorEventType type,
            String deviceEui,
            Instant from,
            Instant to,
            Integer page,
            Integer size
    ) {
        // 인가는 findAll이 건다 — 기기 마스터와 같은 급의 매니저 전용이다
        List<SensorDeviceResult> devices = sensorDeviceService.findAll(cohortId, requesterId);
        Set<String> allowedDeviceEuis = new HashSet<>();
        Map<String, String> displayNames = new HashMap<>();
        for (SensorDeviceResult device : devices) {
            allowedDeviceEuis.add(device.deviceEui());
            if (device.displayName() != null) {
                displayNames.put(device.deviceEui(), device.displayName());
            }
        }

        SensorEventQuery query = SensorEventQuery.of(type, deviceEui, from, to, page, size, clock.instant());

        List<SensorEvent> found = sensorEventStore.findByReceivedAt(query.from(), query.to());

        List<SensorEvent> filtered = new ArrayList<>();
        for (SensorEvent event : found) {
            if (allowedDeviceEuis.contains(event.detection().deviceEui())
                    && query.matches(event)) {
                filtered.add(event);
            }
        }

        return toPage(filtered, query, displayNames);
    }

    private SensorEventPage toPage(
            List<SensorEvent> events,
            SensorEventQuery query,
            Map<String, String> displayNames
    ) {
        int totalElements = events.size();
        int totalPages = (totalElements + query.size() - 1) / query.size();

        long offset = query.offset();
        if (offset >= totalElements) {
            return new SensorEventPage(List.of(), query.page(), query.size(), totalElements, totalPages);
        }

        int start = (int) offset;
        int end = Math.min(start + query.size(), totalElements);
        List<SensorEventItem> items = attachDisplayNames(
                events.subList(start, end),
                displayNames
        );

        return new SensorEventPage(items, query.page(), query.size(), totalElements, totalPages);
    }

    private List<SensorEventItem> attachDisplayNames(
            List<SensorEvent> events,
            Map<String, String> displayNames
    ) {
        List<SensorEventItem> items = new ArrayList<>();
        for (SensorEvent event : events) {
            String displayName = displayNames.get(event.detection().deviceEui());
            items.add(new SensorEventItem(event, displayName));
        }

        return items;
    }
}

package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.environment.application.port.SensorEventStore;
import site.omagotchi.learningservice.environment.application.query.SensorEventItem;
import site.omagotchi.learningservice.environment.application.query.SensorEventPage;
import site.omagotchi.learningservice.environment.application.query.SensorEventQuery;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.rule.application.SensorDeviceService;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** 레디스 조회 서비스 */
@RequiredArgsConstructor
@Service
public class SensorEventQueryService {

    private final SensorEventStore sensorEventStore;
    private final SensorDeviceService sensorDeviceService;
    private final Clock clock;

    /** 페이지 단위로 센서이벤트 조회, 각 센서이벤트마다 센서 displayㅣㅣㅏㅣ*/
    public SensorEventPage getEvents(
            SensorEventType type,
            String deviceEui,
            Instant from,
            Instant to,
            Integer page,
            Integer size
    ) {
        SensorEventQuery query = SensorEventQuery.of(type, deviceEui, from, to, page, size, clock.instant());

        List<SensorEvent> found = sensorEventStore.findByReceivedAt(query.from(), query.to());

        List<SensorEvent> filtered = new ArrayList<>();
        for(SensorEvent event : found){
            if(query.matches(event)){
                filtered.add(event);
            }
        }

        return toPage(filtered, query);
    }

    private SensorEventPage toPage(List<SensorEvent> events, SensorEventQuery query){
        int totalElements = events.size();
        int totalPages = (totalElements + query.size() - 1) / query.size();

        long offset = query.offset();
        if(offset >= totalElements){
            return new SensorEventPage(List.of(), query.page(), query.size(), totalElements, totalPages);
        }

        int start = (int) offset;
        int end = Math.min(start + query.size(), totalElements);
        List<SensorEventItem> items = attachDisplayNames(events.subList(start, end));

        return new SensorEventPage(items, query.page(), query.size(), totalElements, totalPages);
    }

    private List<SensorEventItem> attachDisplayNames(List<SensorEvent> events){

        Map<String, String> displayNames = sensorDeviceService.findDisplayNames();

        List<SensorEventItem> items = new ArrayList<>();
        for(SensorEvent event : events){
            String displayName = displayNames.get(event.detection().deviceEui());
            items.add(new SensorEventItem(event, displayName));
        }

        return items;
    }
}

package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.environment.application.port.SensorEventStore;
import site.omagotchi.learningservice.environment.domain.SensorEvent;

import java.time.Instant;
import java.util.List;
/** 레디스 저장, 조회 서비스 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SensorEventRecordService {

    private final SensorEventStore sensorEventStore;

    public void record(SensorEvent sensorEvent){
        sensorEventStore.save(sensorEvent);
    }

    public List<SensorEvent> find(Instant from, Instant to){
        return sensorEventStore.findByReceivedAt(from, to);
    }
}

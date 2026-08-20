package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.environment.application.port.SensorEventStore;
import site.omagotchi.learningservice.environment.domain.ActionOutcome;
import site.omagotchi.learningservice.environment.domain.SensorEvent;

/** 조치후 결과 레디스 저장 서비스 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SensorEventRecordService {

    private final SensorEventStore sensorEventStore;
    private final IotActionDispatcher dispatcher;

    /**IotActionDispatcher를 통해 제어기기에 조치를 요청한 결과를 받아내 레디스 캐싱 */
    public void record(SensorEvent sensorEvent){
        ActionOutcome outcome = dispatcher.dispatch(sensorEvent);
        sensorEventStore.save(sensorEvent.withOutcome(outcome));
    }

}

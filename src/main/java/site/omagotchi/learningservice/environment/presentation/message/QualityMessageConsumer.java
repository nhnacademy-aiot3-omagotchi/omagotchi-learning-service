package site.omagotchi.learningservice.environment.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.environment.application.SensorEventRecordService;

/** RabbitMQ 메세지 진입점- 품질 메세지를 소비함.*/
@Slf4j
@RequiredArgsConstructor
@Component
public class QualityMessageConsumer {
    private static final String CONSUME_QUEUE = "omagotchi.sensor.quality.queue";
    private final SensorEventRecordService recordService;

    @RabbitListener(queues = CONSUME_QUEUE, concurrency = "3-5")
    public void consume(QualityEventMessage message){
        recordService.record(message.toSensorEvent());
    }
}
package site.omagotchi.learningservice.environment.presentation.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** RabbitMQ 메세지 진입점- 품질 메세지를 소비함.*/
@Slf4j
@Component
public class QualityMessageConsumer {
    private static final String CONSUME_QUEUE = "omagotchi.sensor.quality.queue";

    @RabbitListener(queues = CONSUME_QUEUE)
    public void consume(QualityEvent qualityEvent, @Header(name = "traceId", required = false) String traceId){
        if(qualityEvent.type() == QualityEvent.Type.RULE_HIT){
            log.info("룰 히트 데이터 발생");
        }else{
            log.info("{} 데이터 발생", qualityEvent.type().name());
        }
    }
}
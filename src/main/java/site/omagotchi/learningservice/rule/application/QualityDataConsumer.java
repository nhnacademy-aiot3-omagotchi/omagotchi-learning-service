package site.omagotchi.learningservice.rule.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.rule.domain.QualityEvent;

//TODO: 현재는 소비는 하되 관리자쪽에 어떻게 표현할지 확정된게 없음. 로그만 찍도록 일단 세팅
@Slf4j
@Component
public class QualityDataConsumer {
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

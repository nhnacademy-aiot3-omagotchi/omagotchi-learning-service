package site.omagotchi.learningservice.rule.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import site.omagotchi.learningservice.rule.application.event.ThresholdRuleChangedEvent;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ThresholdRuleChangedPublisher {
    private static final long CONFIRM_TIME_OUT_MS = 5000L;
    private final RabbitTemplate rabbitTemplate;
    private final Counter publishFailed;

    public ThresholdRuleChangedPublisher(RabbitTemplate rabbitTemplate, MeterRegistry registry){
        this.rabbitTemplate = rabbitTemplate;
        this.publishFailed = registry.counter("rule.changed.publish.failed");
    }

    /**
     * 커밋이 성공한 경우에만 실행된다.
     *
     * </p>
     * DB는 롤백할 수 있지만 브로커로 나간 메시지는 되돌릴 수 없다. 트랜잭션 안에서 발행하면
     * 그 뒤의 이력 저장이나 커밋 자체가 실패했을 때 룰은 사라지고 메시지만 남는다.
     * 그러면 rule-service 캐시에 DB에 존재한 적 없는 룰이 들어가, 재동기화가 도는 최대 5분 동안
     * 그 값으로 센서를 판정한다.
     *
     * 해당 문제를 해결하기위해 커밋후 실행하도록 설정
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ThresholdRuleChangedEvent event){
        CorrelationData correlation = new CorrelationData(event.ruleId() + ":" + event.ruleVersion());

        try{
            rabbitTemplate.convertAndSend(
                    RabbitTopologyConfig.EXCHANGE_RULE_CHANGED,
                    "",
                    ThresholdRuleChangedMessage.from(event),
                    correlation
            );

            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIME_OUT_MS, TimeUnit.MILLISECONDS);

            if(!confirm.ack()){
                publishFailed.increment();
                log.error("rule.changed 브로커 응답 실패 ruleId={} v={}, resason={}", event.ruleId(), event.ruleVersion(), confirm.reason());
            }
            log.info("rule.changed 발행 ruleId={}, v={}", event.ruleId(), event.ruleVersion());

        }catch (Exception e){
            publishFailed.increment();
            log.error("rule.changed 발행 실패 ruleId={}, v={}", event.ruleId(), event.ruleVersion(), e);
        }
    }
}

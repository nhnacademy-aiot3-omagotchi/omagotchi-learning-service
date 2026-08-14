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

    /** 브로커 확인을 기다리는 상한. 초과하면 발행 실패로 간주한다. */
    private static final long CONFIRM_TIMEOUT_MS = 5_000L;

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
                    correlation.getFuture().get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if(!confirm.ack()){
                publishFailed.increment();
                log.error("rule.changed nack ruleId={}, v={}, reason={} — 재동기화가 보정",
                        event.ruleId(), event.ruleVersion(), confirm.reason()); // 브로커가 nack를 보내도 재발행하지않음 왜? -> rule-service에서 5분 재발행해줘서 굳이...
                return;
            }

            // "구독자에게 도달했다"가 아니라 "브로커가 받았다"다 (위 주석 참고)
            log.info("rule.changed 브로커 수신 ruleId={}, v={}", event.ruleId(), event.ruleVersion());

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            publishFailed.increment();
            log.error("rule.changed 발행 대기 중단 ruleId={}, v={}", event.ruleId(), event.ruleVersion(), e);

        }catch (Exception e){
            publishFailed.increment();
            log.error("rule.changed 발행 실패 ruleId={}, v={}", event.ruleId(), event.ruleVersion(), e);
        }
    }
}

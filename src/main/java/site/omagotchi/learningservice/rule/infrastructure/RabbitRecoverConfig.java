package site.omagotchi.learningservice.rule.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

@Slf4j
@Configuration
public class RabbitRecoverConfig {
    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate, RecoveryMetrics metrics){
        RepublishMessageRecoverer delegate = new RepublishMessageRecoverer(rabbitTemplate, RabbitTopologyConfig.EXCHANGE_QUALITY_DEAD_LETTER);

        return (message, cause) -> {

            // @RabbitListener 에서 발생한 예외는 Spring AMQP가 ListenerExecutionFailedException으로 감싸서 넘김
            // getMostSpecificCause()를 통해서 원래 원인이 최상위 예외를 뽑아냄
            Throwable root = NestedExceptionUtils.getMostSpecificCause(cause);
            MessageProperties properties = message.getMessageProperties();


            metrics.countedParked(root);
            log.error("품질 이벤트 처리 실패. traceId={}, routingKey={}",
                    properties.getHeader("traceId"), properties.getReceivedRoutingKey());

            //실제로 DLQ로 이관함. 이때 헤더를 추가해줌
            //x-exception-stacktrace, x-exception-message, x-original-exchange, x-original-routingKey
            delegate.recover(message, cause);
        };
    }
}

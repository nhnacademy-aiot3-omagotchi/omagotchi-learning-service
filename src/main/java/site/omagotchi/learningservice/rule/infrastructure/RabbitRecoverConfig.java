package site.omagotchi.learningservice.rule.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

@Slf4j
@Configuration
public class RabbitRecoverConfig {

    /** 브로커 확인을 기다리는 상한. 초과하면 발행 실패로 간주한다. */
    private static final long CONFIRM_TIMEOUT_MS = 5_000L;

    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate, RecoveryMetrics metrics){
        // RepublishMessageRecoverer 는 발행 후 곧바로 반환해서 브로커가 실제로 받았는지 모른다.
        // WithConfirms 는 publisher confirm 을 기다렸다가 nack·타임아웃이면 예외를 던지므로,
        // 정상 반환한 건수만 집계할 수 있다.
        // ConfirmType 은 application.yaml 의 publisher-confirm-type 과 일치해야 한다.
        RepublishMessageRecovererWithConfirms delegate = new RepublishMessageRecovererWithConfirms(
                rabbitTemplate,
                RabbitTopologyConfig.EXCHANGE_QUALITY_DEAD_LETTER,
                ConfirmType.CORRELATED
        );
        delegate.setConfirmTimeout(CONFIRM_TIMEOUT_MS);

        return (message, cause) -> {

            // @RabbitListener 에서 발생한 예외는 Spring AMQP가 ListenerExecutionFailedException으로 감싸서 넘김
            // getMostSpecificCause()를 통해서 원래 원인이 최상위 예외를 뽑아냄
            Throwable root = NestedExceptionUtils.getMostSpecificCause(cause);
            MessageProperties properties = message.getMessageProperties();


            log.error("품질 이벤트 처리 실패. traceId={}, routingKey={}",
                    properties.getHeader("traceId"), properties.getReceivedRoutingKey());

            //실제로 DLQ로 이관함. 이때 헤더를 추가해줌
            //x-exception-stacktrace, x-exception-message, x-original-exchange, x-original-routingKey
            try{
                delegate.recover(message, cause);
            }catch (Exception e){
                log.error("파킹 큐 이관 실패 - requeue. traceId={}", properties.getHeader("traceId"), e);
                throw new ImmediateRequeueAmqpException("파킹 큐 이관 실패", e);
            }

            // 발행이 브로커에 확인된 뒤에만 집계한다.
            // 위에서 예외가 나면 여기 도달하지 않으므로 미발행 메시지가 섞이지 않는다.
            metrics.countedParked(root);
        };
    }
}

package site.omagotchi.learningservice.environment.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

@Slf4j
@Configuration
public class RabbitRecoverConfig {

    /** 브로커 확인을 기다리는 상한. 초과하면 발행 실패로 간주한다. */
    private static final long CONFIRM_TIMEOUT_MS = 5_000L;

    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate,
                                             RecoveryMetrics metrics,
                                             RabbitProperties rabbitProperties){

        ConfirmType confirmType = requireCorrelated(rabbitProperties.getPublisherConfirmType());

        RepublishMessageRecovererWithConfirms delegate = new RepublishMessageRecovererWithConfirms(
                rabbitTemplate,
                QualityTopologyConfig.EXCHANGE_QUALITY_DEAD_LETTER,
                confirmType
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

    private static ConfirmType requireCorrelated(ConfirmType confirmType) {
        if (confirmType != ConfirmType.CORRELATED) {
            throw new IllegalStateException(
                    "spring.rabbitmq.publisher-confirm-type 은 correlated 여야 합니다. 현재=" + confirmType
            );
        }
        return confirmType;
    }
}

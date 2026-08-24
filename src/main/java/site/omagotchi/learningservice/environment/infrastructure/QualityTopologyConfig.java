package site.omagotchi.learningservice.environment.infrastructure;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QualityTopologyConfig {
    public static final String EXCHANGE_QUALITY_DEAD_LETTER = "omagotchi.sensor.quality.dead-letter.exchange";
    public static final String QUEUE_QUALITY_DEAD_LETTER = "omagotchi.sensor.quality.dead-letter.queue";

    private static final long TTL_30_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long MAX_LENGTH = 20_000L;

    @Bean
    public Queue queueQualityDeadLetter(){
        return QueueBuilder.durable(QUEUE_QUALITY_DEAD_LETTER)
                .maxLength(MAX_LENGTH)
                .withArgument("x-message-ttl", TTL_30_DAYS_MS)
                .overflow(QueueBuilder.Overflow.dropHead)
                .build();
    }

    @Bean
    public FanoutExchange exchangeQualityDeadLetter(){
        return ExchangeBuilder.fanoutExchange(EXCHANGE_QUALITY_DEAD_LETTER).durable(true).build();
    }

    @Bean
    public Binding qualityDeadLetterBinding() {
        return BindingBuilder.bind(queueQualityDeadLetter()).to(exchangeQualityDeadLetter());
    }

}

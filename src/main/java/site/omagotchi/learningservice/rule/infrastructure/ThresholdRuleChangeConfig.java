package site.omagotchi.learningservice.rule.infrastructure;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;

public class ThresholdRuleChangeConfig {
    public static final String EXCHANGE_RULE_CHANGED = "omagotchi.rule.changed.exchange";


    @Bean
    public FanoutExchange exchangeUpdated(){
        return ExchangeBuilder.fanoutExchange(EXCHANGE_RULE_CHANGED)
                .durable(true)
                .build();
    }
}

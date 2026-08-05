package site.omagotchi.learningservice.study.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class TimerTimeConfig {

    @Bean
    public TimerTimePolicy timerTimePolicy(
            @Value("${timer.max-duration}") Duration maxDuration
    ) {
        return new TimerTimePolicy(maxDuration);
    }
}

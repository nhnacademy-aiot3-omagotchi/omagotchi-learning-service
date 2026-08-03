package site.omagotchi.learningservice.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

@Configuration
public class ClockConfig {

    @Bean
    public Clock utcClock() {
        return Clock.tickSeconds(ZoneOffset.UTC);
    }
}

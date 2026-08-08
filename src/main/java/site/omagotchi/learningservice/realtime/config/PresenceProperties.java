package site.omagotchi.learningservice.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "realtime.presence")
public record PresenceProperties(
        Duration sessionTtl
) {
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofSeconds(60);

    public PresenceProperties {
        sessionTtl = sessionTtl == null ? DEFAULT_SESSION_TTL : sessionTtl;
    }
}

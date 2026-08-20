package site.omagotchi.learningservice.realtime.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Presence 세션 유지 시간 정책이다.
 */
@ConfigurationProperties(prefix = "realtime.presence")
public record PresenceProperties(
        Duration sessionTtl
) {
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofSeconds(60);

    public PresenceProperties {
        sessionTtl = sessionTtl == null ? DEFAULT_SESSION_TTL : sessionTtl;
    }
}

package site.omagotchi.learningservice.study.domain;

import java.time.Duration;
import java.time.Instant;

public final class TimerTimePolicy {

    private final Duration maxDuration;

    public TimerTimePolicy(Duration maxDuration) {
        if (maxDuration == null || !maxDuration.isPositive()) {
            throw new IllegalArgumentException("최대 실행 시간은 0보다 커야 합니다.");
        }
        this.maxDuration = maxDuration;
    }

    public Instant expirationAt(Instant startedAt) {
        return startedAt.plus(maxDuration);
    }

    public boolean isExpired(Instant startedAt, Instant currentAt) {
        return !currentAt.isBefore(expirationAt(startedAt));
    }
}

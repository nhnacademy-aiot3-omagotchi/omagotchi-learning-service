package site.omagotchi.learningservice.study.domain;

import java.time.Duration;
import java.time.Instant;

public final class TimerTimePolicy {

    private final Duration maxDuration;

    public TimerTimePolicy(Duration maxDuration) {
        if (maxDuration == null || maxDuration.isZero() || maxDuration.isNegative()) {
            throw new IllegalArgumentException("최대 실행 시간은 0보다 커야 합니다.");
        }
        this.maxDuration = maxDuration;
    }

    public Instant expirationAt(Instant startedAt) {
        validateInstant(startedAt, "startedAt");
        return startedAt.plus(maxDuration);
    }

    public boolean isExpired(Instant startedAt, Instant currentAt) {
        validateInstant(currentAt, "currentAt");
        return !currentAt.isBefore(expirationAt(startedAt));
    }

    public long elapsedSeconds(Instant startedAt, Instant currentAt) {
        validateInstant(startedAt, "startedAt");
        validateInstant(currentAt, "currentAt");

        return Math.max(0L, Duration.between(startedAt, currentAt).getSeconds());
    }

    private void validateInstant(Instant instant, String fieldName) {
        if (instant == null) {
            throw new IllegalArgumentException(fieldName + "이 null입니다.");
        }
    }
}

package site.omagotchi.learningservice.team.application.port;

import java.time.Instant;
import java.util.Objects;

/** Identity가 소유한 현재 계정 상태와 해당 상태의 시작 시각. */
public record IdentityAccountSnapshot(
        IdentityAccountState status,
        Instant statusChangedAt
) {
    public IdentityAccountSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(statusChangedAt, "statusChangedAt");
    }
}

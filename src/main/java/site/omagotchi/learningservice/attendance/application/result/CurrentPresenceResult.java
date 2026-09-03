package site.omagotchi.learningservice.attendance.application.result;

import site.omagotchi.learningservice.attendance.domain.PresenceState;

import java.time.Instant;

/** 현재 열린 체류구간의 위치 정보. */
public record CurrentPresenceResult(
        Long spaceId,
        PresenceState state,
        Instant startedAt
) {
}

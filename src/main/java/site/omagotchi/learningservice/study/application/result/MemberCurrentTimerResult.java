package site.omagotchi.learningservice.study.application.result;

import java.time.Instant;

public record MemberCurrentTimerResult(
        Long cohortMembershipId,
        Instant timerStartedAt,
        long currentAggregationSeconds
) {

    public MemberCurrentTimerResult {
        if (cohortMembershipId == null
                || timerStartedAt == null
                || currentAggregationSeconds < 0L) {
            throw new IllegalArgumentException("현재 타이머 정보가 올바르지 않습니다.");
        }
    }
}

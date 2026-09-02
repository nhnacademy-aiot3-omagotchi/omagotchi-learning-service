package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.util.UUID;

public record MemberSummaryResult(
        Long cohortMembershipId,
        UUID userId,
        long todayStudySeconds,
        long periodStudySeconds,
        long activeStudyDays,
        long recordCount,
        Instant lastStudiedAt,
        boolean isRunning,
        Instant timerStartedAt
) {

    public MemberSummaryResult {
        if (isRunning != (timerStartedAt != null)) {
            throw new IllegalArgumentException(
                    "isRunning과 timerStartedAt은 함께 설정되어야 합니다."
            );
        }
    }

    public MemberSummaryResult(
            Long cohortMembershipId,
            UUID userId,
            long todayStudySeconds,
            long periodStudySeconds,
            long activeStudyDays,
            long recordCount,
            Instant lastStudiedAt
    ) {
        this(
                cohortMembershipId,
                userId,
                todayStudySeconds,
                periodStudySeconds,
                activeStudyDays,
                recordCount,
                lastStudiedAt,
                false,
                null
        );
    }

    public MemberSummaryResult withRunningTimer(Instant startedAt) {
        return new MemberSummaryResult(
                cohortMembershipId,
                userId,
                todayStudySeconds,
                periodStudySeconds,
                activeStudyDays,
                recordCount,
                lastStudiedAt,
                true,
                startedAt
        );
    }
}

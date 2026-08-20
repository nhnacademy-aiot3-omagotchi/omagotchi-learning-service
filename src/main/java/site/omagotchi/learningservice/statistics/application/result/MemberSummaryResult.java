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
        Instant lastStudiedAt
) {
}

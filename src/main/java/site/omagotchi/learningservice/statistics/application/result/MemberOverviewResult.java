package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemberOverviewResult(
        Long cohortMembershipId,
        UUID userId,
        String window,
        LocalDate from,
        LocalDate to,
        Instant calculatedAt,
        long totalStudySeconds,
        long averageDailyStudySeconds,
        long activeStudyDays,
        long recordCount,
        Instant lastStudiedAt,
        List<DailyTotalResult> dailyTotals
) {

    public MemberOverviewResult {
        dailyTotals = List.copyOf(dailyTotals);
    }
}

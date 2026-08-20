package site.omagotchi.learningservice.statistics.presentation.response;

import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberOverviewResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemberOverviewResponse(
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
        List<DailyTotal> dailyTotals
) {

    public MemberOverviewResponse {
        dailyTotals = List.copyOf(dailyTotals);
    }

    public static MemberOverviewResponse from(
            MemberOverviewResult result
    ) {
        return new MemberOverviewResponse(
                result.cohortMembershipId(),
                result.userId(),
                result.window(),
                result.from(),
                result.to(),
                result.calculatedAt(),
                result.totalStudySeconds(),
                result.averageDailyStudySeconds(),
                result.activeStudyDays(),
                result.recordCount(),
                result.lastStudiedAt(),
                result.dailyTotals().stream()
                        .map(DailyTotal::from)
                        .toList()
        );
    }

    public record DailyTotal(
            LocalDate aggregationDate,
            long studySeconds
    ) {

        private static DailyTotal from(DailyTotalResult result) {
            return new DailyTotal(result.aggregationDate(), result.studySeconds());
        }
    }
}

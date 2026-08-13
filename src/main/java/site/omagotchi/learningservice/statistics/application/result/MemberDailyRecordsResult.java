package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemberDailyRecordsResult(
        Long cohortMembershipId,
        UUID userId,
        LocalDate date,
        Instant calculatedAt,
        long totalStudySeconds,
        List<MemberDailyRecordResult> records
) {

    public MemberDailyRecordsResult {
        records = List.copyOf(records);
    }
}

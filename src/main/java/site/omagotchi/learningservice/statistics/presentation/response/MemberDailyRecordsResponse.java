package site.omagotchi.learningservice.statistics.presentation.response;

import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordsResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemberDailyRecordsResponse(
        Long cohortMembershipId,
        UUID userId,
        LocalDate date,
        Instant calculatedAt,
        long totalStudySeconds,
        List<Record> records
) {

    public MemberDailyRecordsResponse {
        records = List.copyOf(records);
    }

    public static MemberDailyRecordsResponse from(
            MemberDailyRecordsResult result
    ) {
        return new MemberDailyRecordsResponse(
                result.cohortMembershipId(),
                result.userId(),
                result.date(),
                result.calculatedAt(),
                result.totalStudySeconds(),
                result.records().stream()
                        .map(Record::from)
                        .toList()
        );
    }

    public record Record(
            UUID id,
            Instant startTime,
            Instant endTime,
            long studySeconds
    ) {

        private static Record from(MemberDailyRecordResult result) {
            return new Record(
                    result.id(),
                    result.startTime(),
                    result.endTime(),
                    result.studySeconds()
            );
        }
    }
}

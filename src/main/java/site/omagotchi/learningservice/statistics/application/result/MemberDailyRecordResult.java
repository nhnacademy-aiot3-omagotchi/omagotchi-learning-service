package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.util.UUID;

public record MemberDailyRecordResult(
        UUID id,
        Instant startTime,
        Instant endTime,
        long studySeconds
) {
}

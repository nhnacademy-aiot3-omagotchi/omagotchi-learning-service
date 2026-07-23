package site.omagotchi.learningservice.study.application.command;

import java.time.Instant;

public record CreateStudyRecordCommand(
        Long cohortId,
        Instant startTime,
        Instant endTime
) {
}

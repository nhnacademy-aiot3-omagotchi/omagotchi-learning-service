package site.omagotchi.learningservice.study.application.command;

import java.time.Instant;

public record CreateStudyRecordCommand(
        Instant startTime,
        Instant endTime
) {
}

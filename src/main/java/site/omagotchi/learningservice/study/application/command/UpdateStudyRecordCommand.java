package site.omagotchi.learningservice.study.application.command;

import java.time.Instant;

public record UpdateStudyRecordCommand(
        Instant startTime,
        Instant endTime,
        Long expectedVersion
) {
}

package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.StudyRecordResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudyRecordResponse(
        UUID id,
        Long cohortMembershipId,
        LocalDate aggregationDate,
        Instant startTime,
        Instant endTime,
        Long studySeconds,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static StudyRecordResponse from(StudyRecordResult result) {
        return new StudyRecordResponse(
                result.id(),
                result.cohortMembershipId(),
                result.aggregationDate(),
                result.startTime(),
                result.endTime(),
                result.studySeconds(),
                result.version(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}

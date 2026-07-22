package site.omagotchi.learningservice.study.application.result;

import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudyRecordResult(
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

    public static StudyRecordResult from(StudyRecordEntity entity) {
        return new StudyRecordResult(
                entity.getId(),
                entity.getCohortMembershipId(),
                entity.getAggregationDate(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getStudySeconds(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

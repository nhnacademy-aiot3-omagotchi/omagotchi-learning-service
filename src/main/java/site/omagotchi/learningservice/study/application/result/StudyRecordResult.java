package site.omagotchi.learningservice.study.application.result;

import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudyRecordResult(
        UUID id,
        LocalDate aggregationDate,
        Instant startTime,
        Instant endTime,
        Long studySeconds,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static StudyRecordResult from(StudyRecord entity) {
        return new StudyRecordResult(
                entity.getId(),
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

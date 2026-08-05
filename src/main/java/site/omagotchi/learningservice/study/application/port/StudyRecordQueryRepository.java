package site.omagotchi.learningservice.study.application.port;

import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyRecordQueryRepository {

    Optional<StudyRecord> findActiveByIdAndCohortMembershipId(
            UUID studyRecordId,
            Long cohortMembershipId
    );

    boolean existsActiveOverlap(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            UUID excludedStudyRecordId
    );

    List<StudyRecord> findDailyRecords(
            Long cohortMembershipId,
            LocalDate aggregationDate
    );

    List<DailyStudySecondsResult> findDailyStudySeconds(
            Long cohortMembershipId,
            LocalDate startDate,
            LocalDate endDateInclusive
    );
}

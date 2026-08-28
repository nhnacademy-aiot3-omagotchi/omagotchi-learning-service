package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StudyRecordOverlapGuard {

    private final StudyRecordQueryRepository studyRecordQueryRepository;

    // StudyRecord 저장 전에 중복 구간을 repository 호출로 조회
    public void requireNoOverlap(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            UUID excludedStudyRecordId
    ) {
        if (hasOverlap(
                cohortMembershipId,
                startTime,
                endTime,
                excludedStudyRecordId
        )) {
            throw new BusinessException(StudyRecordErrorCode.OVERLAP);
        }
    }

    public boolean hasOverlap(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            UUID excludedStudyRecordId
    ) {
        return studyRecordQueryRepository.existsActiveOverlap(
                cohortMembershipId,
                startTime,
                endTime,
                excludedStudyRecordId
        );
    }
}

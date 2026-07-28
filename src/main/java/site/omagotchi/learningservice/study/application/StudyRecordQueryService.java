package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.domain.entity.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRecordQueryService {

    private final CohortAccessService cohortAccessService;
    private final StudyRecordRepository studyRecordRepository;

    public StudyRecordResult getRecord(
            UUID userId,
            Long cohortId,
            UUID studyRecordId
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        StudyRecord entity = studyRecordRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        return StudyRecordResult.from(entity);
    }
}

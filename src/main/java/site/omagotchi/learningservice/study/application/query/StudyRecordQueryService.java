package site.omagotchi.learningservice.study.application.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRecordQueryService {

    private final StudyRecordRepository studyRecordRepository;

    public StudyRecordResult getRecord(
            Long cohortMembershipId,
            UUID studyRecordId
    ) {
        StudyRecordEntity entity = studyRecordRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        return StudyRecordResult.from(entity);
    }
}

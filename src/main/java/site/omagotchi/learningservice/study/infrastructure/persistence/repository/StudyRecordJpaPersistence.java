package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.domain.StudyRecord;

@Repository
@RequiredArgsConstructor
public class StudyRecordJpaPersistence implements StudyRecordRepository {

    private final StudyRecordJpaRepository repository;

    @Override
    public StudyRecord save(StudyRecord studyRecord) {
        return repository.save(studyRecord);
    }

    @Override
    public StudyRecord saveWithVersionCheck(StudyRecord studyRecord) {
        try {
            return repository.saveAndFlush(studyRecord);
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(StudyRecordErrorCode.VERSION_CONFLICT);
        }
    }
}

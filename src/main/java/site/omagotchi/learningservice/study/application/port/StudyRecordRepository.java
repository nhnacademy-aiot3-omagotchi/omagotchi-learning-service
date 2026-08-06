package site.omagotchi.learningservice.study.application.port;

import site.omagotchi.learningservice.study.domain.StudyRecord;

public interface StudyRecordRepository {
    StudyRecord save(StudyRecord studyRecord);

    StudyRecord saveWithVersionCheck(StudyRecord studyRecord);
}

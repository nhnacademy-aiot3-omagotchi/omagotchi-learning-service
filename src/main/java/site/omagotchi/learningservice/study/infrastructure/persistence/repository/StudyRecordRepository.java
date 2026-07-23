package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;

import java.util.UUID;

public interface StudyRecordRepository extends
        JpaRepository<StudyRecordEntity, UUID>,
        StudyRecordRepositoryCustom {
}

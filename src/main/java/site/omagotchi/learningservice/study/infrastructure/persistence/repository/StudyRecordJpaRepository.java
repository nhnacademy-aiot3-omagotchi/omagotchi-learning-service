package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.util.UUID;

public interface StudyRecordJpaRepository extends JpaRepository<StudyRecord, UUID> {
}

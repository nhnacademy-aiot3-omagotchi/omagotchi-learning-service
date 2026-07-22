package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;

import java.util.UUID;

public interface StudyRecordRepository extends JpaRepository<StudyRecordEntity, UUID> {

    // TODO(REC-005): 소유권·활성 상태·집계 일자 기준의 단순 조회 메서드를 추가한다.
    // TODO(OVL-001, DAT-004): 겹침·제외 대상·분할 청크 조회는 QueryDSL 전용 Repository로 분리한다.
}

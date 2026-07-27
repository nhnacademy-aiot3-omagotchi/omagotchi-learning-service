package site.omagotchi.learningservice.cohort.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.cohort.domain.CohortAuditLog;

import java.util.List;

public interface CohortAuditLogRepository extends JpaRepository<CohortAuditLog, Long> {

    List<CohortAuditLog> findByCohortIdOrderByOccurredAtDesc(Long cohortId);
}

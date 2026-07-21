package site.omagotchi.learningservice.cohort.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;

public interface CohortAttendancePolicyRepository extends JpaRepository<CohortAttendancePolicy, Long> {
}

package site.omagotchi.learningservice.cohort.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;

import java.util.Optional;

public interface CohortJoinCodeRepository extends JpaRepository<CohortJoinCode, Long> {

    Optional<CohortJoinCode> findFirstByCohortIdAndStatusOrderByIssuedAtDesc(
            Long cohortId,
            CohortJoinCodeStatus status
    );

    Optional<CohortJoinCode> findByCodeHash(String codeHash);
}

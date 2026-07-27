package site.omagotchi.learningservice.cohort.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

import java.util.List;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findByStatus(CohortStatus status);
}

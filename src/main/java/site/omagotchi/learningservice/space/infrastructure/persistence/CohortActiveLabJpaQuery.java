package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.application.port.CohortActiveLabQuery;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

/** cohort가 소유한 활성 LAB 조건을 space 저장소로 구현한다. */
@Repository
@RequiredArgsConstructor
public class CohortActiveLabJpaQuery implements CohortActiveLabQuery {

    private final SpringDataSpaceRepository springDataSpaceRepository;

    @Override
    public boolean existsActiveLab(Long cohortId) {
        return springDataSpaceRepository.existsActiveLabByCohortId(cohortId);
    }
}
